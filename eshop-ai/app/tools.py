"""工具层（P5-4）：只读的订单 / 商品 / 库存查询。

权限红线（设计文档 §6）：
    userId 由 `build_tools()` 闭包捕获，**不出现在工具参数 schema 里**。
    模型只能决定"查什么"，不能决定"查谁"。
    未登录时干脆不绑定订单类工具，从根上杜绝诱导模型查他人订单。

调用方式：
    直连 Java 服务（127.0.0.1:8085/8082/8083）并带 X-User-Id，
    不走网关回环——AI 服务手里只有 header、没有用户 JWT，绕回网关必然 401。
"""

from __future__ import annotations

import logging
import re
from typing import Any
from urllib.parse import quote

import httpx
from langchain_core.tools import tool

from .config import get_settings
from .text import normalize_for_bm25

log = logging.getLogger(__name__)

TIMEOUT = 6.0

PRICE_RE = re.compile(r"(\d+)\s*(?:元|块)?\s*(?:以内|以下|之内)")
ORDER_NO_RE = re.compile(r"[A-Za-z]{2}\d{10,}")
PRODUCT_NOISE = (
    "推荐", "有货吗", "有货", "多少钱", "价格", "库存", "尺码", "颜色", "型号",
    "适合", "几款", "一些", "帮我", "找找", "看看", "查查", "有没有",
)


def extract_product_query(text: str) -> tuple[str, float]:
    """从「500以内耳机推荐」这类句子里抽出 (关键词, 价格上限)。

    模型不一定肯调工具，这里是确定性兜底：抽错了顶多搜不到，不会编数据。
    """
    normalized = normalize_for_bm25(text)
    match = PRICE_RE.search(normalized)
    max_price = float(match.group(1)) if match else 0.0

    rest = PRICE_RE.sub("", normalized)
    for word in PRODUCT_NOISE:
        rest = rest.replace(word, "")
    keyword = rest.strip(" 的了吗呢，。！？、,.?!")

    return (keyword or normalized.strip()), max_price


def extract_order_no(text: str) -> str | None:
    match = ORDER_NO_RE.search(text or "")
    return match.group(0) if match else None


def _ok_body(resp: httpx.Response) -> dict:
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 200:
        raise RuntimeError(body.get("message") or f"业务错误 {body.get('code')}")
    return body.get("data") or {}


def _money(value: Any) -> str:
    try:
        return f"{float(value):.2f}"
    except Exception:
        return "-"


def _short(name: Any, limit: int = 14) -> str:
    """商品名常带「｜」副标题，链接标签只用主名，太长截断。"""
    text = str(name or "").split("｜")[0].strip()
    return text if len(text) <= limit else text[:limit] + "…"


def _emit_links(sink: list | None, items: list[dict]) -> None:
    """把前端可点的跳转链接收进侧通道，不参与给模型的文本。"""
    if sink is None:
        return
    for item in items:
        if item.get("url") and item not in sink:
            sink.append(item)


def build_tools(user_id: int | None, logged_in: bool, sink: list | None = None) -> list:
    """按登录态裁剪工具集合。

    sink 是「跳转链接」的收集器：工具把结构化链接塞进去，接入层再随响应下发，
    这样前端拿到的 URL 都是后端拼的，不依赖模型生成（模型编 URL 是灾难）。
    """
    settings = get_settings()
    order_base = settings.java_order_url
    product_base = settings.java_product_url
    stock_base = settings.java_stock_url
    headers = {"X-User-Id": str(user_id)} if logged_in and user_id else {}

    @tool
    async def query_my_orders(status: str = "", limit: int = 5) -> str:
        """查询当前登录用户自己的订单列表（只能查本人）。

        Args:
            status: 订单状态码，可选。0=待支付，1=已支付待发货，2=已发货待收货，
                    3=已完成，4=已取消，5=超时关闭。不确定就留空，表示查全部。
            limit: 最多返回几条，默认 5，最大 10。
        """
        if not logged_in:
            return "用户当前未登录，无法查询订单。请先登录后再试。"

        params: dict[str, Any] = {"page": 1, "size": max(1, min(int(limit or 5), 10))}
        if status and str(status).strip().isdigit():
            params["status"] = int(status)

        try:
            async with httpx.AsyncClient(timeout=TIMEOUT) as client:
                # 注意：各服务控制器没有类级前缀（网关已 StripPrefix=2 去掉 /api/order）
                resp = await client.get(f"{order_base}/page", params=params, headers=headers)
            data = _ok_body(resp)
        except Exception as exc:
            log.warning("query_my_orders 失败：%s", exc)
            return "订单查询失败，请稍后再试或转人工。"

        items = data.get("list") or []
        if not items:
            return "该用户名下没有符合条件的订单。"

        # 侧通道：订单列表页 + 最近几笔的详情页
        _emit_links(sink, [{"label": "查看我的订单", "url": "/orders", "kind": "order"}])
        _emit_links(
            sink,
            [
                {
                    "label": f"订单 {str(o.get('orderNo'))[-6:]}（{o.get('statusText')}）",
                    "url": f"/orders/{o.get('orderNo')}",
                    "kind": "order",
                }
                for o in items[:3]
            ],
        )

        lines = [f"共 {data.get('total', len(items))} 笔订单，以下是最近 {len(items)} 笔："]
        for order in items:
            goods = order.get("items") or []
            first = goods[0].get("spuName") or goods[0].get("name") if goods else ""
            suffix = f" 等 {len(goods)} 件" if len(goods) > 1 else ""
            lines.append(
                f"- 订单号 {order.get('orderNo')}｜{order.get('statusText')}｜"
                f"实付 ¥{_money(order.get('payAmount'))}｜下单 {order.get('createTime')}"
                + (f"｜{first}{suffix}" if first else "")
            )
        return "\n".join(lines)

    @tool
    async def get_order_detail(order_no: str) -> str:
        """查询某一笔订单的详情（商品明细、金额、收货信息、状态）。只能查本人订单。

        Args:
            order_no: 订单号，形如 ES202610190001 的字符串。
        """
        if not logged_in:
            return "用户当前未登录，无法查询订单。请先登录后再试。"
        if not order_no or not str(order_no).strip():
            return "缺少订单号，请向用户确认。"

        try:
            async with httpx.AsyncClient(timeout=TIMEOUT) as client:
                resp = await client.get(f"{order_base}/{str(order_no).strip()}", headers=headers)
            data = _ok_body(resp)
        except Exception as exc:
            log.warning("get_order_detail 失败：%s", exc)
            return "订单详情查询失败，可能订单号不存在或无权查看该订单。"

        goods = data.get("items") or []
        _emit_links(
            sink,
            [
                {
                    "label": f"查看订单 {str(order_no)[-6:]}",
                    "url": f"/orders/{str(order_no).strip()}",
                    "kind": "order",
                }
            ],
        )
        detail_lines = [
            f"订单号：{data.get('orderNo')}",
            f"状态：{data.get('statusText')}",
            f"实付：¥{_money(data.get('payAmount'))}（商品金额 ¥{_money(data.get('totalAmount'))}，运费 ¥{_money(data.get('freight'))}）",
            f"下单时间：{data.get('createTime')}",
        ]
        if data.get("receiverName"):
            detail_lines.append(
                f"收货信息：{data.get('receiverName')} {data.get('receiverPhone') or ''} "
                f"{data.get('receiverAddress') or ''}".strip()
            )
        if goods:
            detail_lines.append("商品明细：")
            for item in goods:
                detail_lines.append(
                    f"  - {item.get('spuName') or item.get('name')}｜{item.get('skuName') or ''}"
                    f"｜¥{_money(item.get('price'))} × {item.get('quantity')}"
                )
        return "\n".join(detail_lines)

    @tool
    async def search_products(keyword: str, max_price: float = 0) -> str:
        """按关键词搜索在售商品，返回名称、价格与 SPU id。

        Args:
            keyword: 搜索词，例如「耳机」「运动鞋」。
            max_price: 价格上限（元）。0 表示不限。超过该价格的商品会被过滤掉。
        """
        if not keyword or not str(keyword).strip():
            return "缺少搜索关键词。"

        try:
            async with httpx.AsyncClient(timeout=TIMEOUT) as client:
                resp = await client.get(
                    f"{product_base}/spu/search",
                    params={"keyword": str(keyword).strip(), "page": 1, "size": 10},
                )
            data = _ok_body(resp)
        except Exception as exc:
            log.warning("search_products 失败：%s", exc)
            return "商品搜索暂时不可用，请稍后再试。"

        items = data.get("list") or []
        if max_price and max_price > 0:
            items = [i for i in items if float(i.get("price") or 0) <= float(max_price)]

        if not items:
            scope = f"{keyword}（{max_price} 元以内）" if max_price else str(keyword)
            return f"没有找到「{scope}」相关的在售商品。"

        # 侧通道：前 3 个商品直达详情页 + 全部结果列表页
        _emit_links(
            sink,
            [
                {
                    "label": f"{_short(item.get('name'))} ¥{_money(item.get('price'))}",
                    "url": f"/detail/{item.get('id')}",
                    "kind": "product",
                }
                for item in items[:3]
            ],
        )
        _emit_links(
            sink,
            [
                {
                    "label": f"查看「{_short(keyword, 8)}」全部结果",
                    "url": f"/list?keyword={quote(str(keyword))}",
                    "kind": "list",
                }
            ],
        )

        lines = [f"找到 {len(items)} 个相关商品："]
        for item in items[:5]:
            lines.append(
                f"- {item.get('name')}｜¥{_money(item.get('price'))}"
                f"｜销量 {item.get('sales', 0)}｜SPU {item.get('id')}"
            )
        return "\n".join(lines)

    @tool
    async def check_stock(sku_id: int) -> str:
        """查询某个 SKU 的实时可售库存。

        Args:
            sku_id: SKU 编号（数字），可从商品详情中获得。
        """
        try:
            async with httpx.AsyncClient(timeout=TIMEOUT) as client:
                resp = await client.get(f"{stock_base}/{int(sku_id)}")
            data = _ok_body(resp)
        except Exception as exc:
            log.warning("check_stock 失败：%s", exc)
            return "库存查询暂时不可用，请稍后再试。"

        available = data.get("available")
        if available is None:
            return "未查询到该 SKU 的库存信息。"
        if int(available) <= 0:
            return f"SKU {sku_id} 目前无货。"
        return f"SKU {sku_id} 当前可售 {available} 件。"

    tools = [search_products, check_stock]
    if logged_in:
        tools = [query_my_orders, get_order_detail, *tools]
    return tools


def tool_names(tools: list) -> list[str]:
    return [t.name for t in tools]
