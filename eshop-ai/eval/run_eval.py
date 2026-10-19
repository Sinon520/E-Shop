"""AI 客服评测脚本。

用法（在 eshop-ai 目录下，先启动服务）：
    python -m eval.run_eval              # 跑全部 30 条
    python -m eval.run_eval --cat policy # 只跑某一类
    python -m eval.run_eval --verbose    # 打印每条的回答

不经过网关，直连 8087，避免限流影响结果；需要登录的用例用 X-User-Id 注入身份。
每条用独立 sessionId，保证是单轮场景（多轮另有用例覆盖）。

通过标准（与设计文档一致）：意图 ≥85%、工具 ≥90%、关键词 ≥85%。
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
import uuid
from pathlib import Path

import httpx

BASE_URL = "http://127.0.0.1:8087"
QUESTIONS = Path(__file__).resolve().parent / "questions.jsonl"
USER_ID = "1"


def load(cat: str | None) -> list[dict]:
    items = []
    for line in QUESTIONS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        item = json.loads(line)
        if cat and item.get("cat") != cat:
            continue
        items.append(item)
    return items


def ask(item: dict, timeout: float = 60.0) -> tuple[dict, float]:
    headers = {"Content-Type": "application/json"}
    if item.get("login"):
        headers["X-User-Id"] = USER_ID

    payload = {"message": item["q"], "sessionId": f"eval-{item['id']}-{uuid.uuid4().hex[:6]}"}
    started = time.perf_counter()
    resp = httpx.post(f"{BASE_URL}/chat", json=payload, headers=headers, timeout=timeout)
    cost = time.perf_counter() - started
    resp.raise_for_status()
    body = resp.json()
    return (body.get("data") or {}), cost


def check(item: dict, data: dict) -> tuple[bool, bool, bool]:
    intent_ok = data.get("intent") == item["intent"]

    expected_tool = item.get("tool")
    used = set(data.get("toolsUsed") or [])
    tool_ok = (not used) if expected_tool is None else (expected_tool in used)

    must = item.get("must") or []
    answer = data.get("answer") or ""
    keyword_ok = True if not must else any(word in answer for word in must)

    return intent_ok, tool_ok, keyword_ok


def main() -> int:
    parser = argparse.ArgumentParser(description="AI 客服评测")
    parser.add_argument("--cat", help="只跑某一类（policy/product/order/escalate/chitchat/edge）")
    parser.add_argument("--verbose", action="store_true", help="打印每条回答")
    args = parser.parse_args()

    items = load(args.cat)
    if not items:
        print("没有可执行的用例")
        return 1

    rows = []
    print(f"{'#':>3} {'类别':<9} {'意图':<6} {'工具':<6} {'关键词':<6} {'耗时':>6}  问题")
    print("-" * 92)

    for item in items:
        try:
            data, cost = ask(item)
            intent_ok, tool_ok, keyword_ok = check(item, data)
        except Exception as exc:
            print(f"{item['id']:>3} {item['cat']:<9} 请求失败：{type(exc).__name__} {exc}")
            rows.append((False, False, False, 0.0, item))
            continue

        mark = lambda ok: "PASS" if ok else "FAIL"  # noqa: E731
        print(
            f"{item['id']:>3} {item['cat']:<9} {mark(intent_ok):<6} {mark(tool_ok):<6} "
            f"{mark(keyword_ok):<6} {cost:>5.2f}s  {item['q'][:34]}"
        )
        if args.verbose or not (intent_ok and tool_ok and keyword_ok):
            print(f"    意图={data.get('intent')} 工具={data.get('toolsUsed')} 回答={data.get('answer', '')[:110]}")

        rows.append((intent_ok, tool_ok, keyword_ok, cost, item))

    total = len(rows)
    intent_acc = sum(1 for r in rows if r[0]) / total
    tool_acc = sum(1 for r in rows if r[1]) / total
    keyword_acc = sum(1 for r in rows if r[2]) / total
    avg_cost = statistics.mean(r[3] for r in rows if r[3] > 0) if total else 0

    print("-" * 92)
    print(
        f"用例 {total} 条｜意图准确率 {intent_acc:.1%}｜工具路由 {tool_acc:.1%}｜"
        f"关键词 {keyword_acc:.1%}｜平均耗时 {avg_cost:.2f}s"
    )

    ok = intent_acc >= 0.85 and tool_acc >= 0.90 and keyword_acc >= 0.85
    print("结论：" + ("PASS（达到验收标准）" if ok else "FAIL（未达标准）"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
