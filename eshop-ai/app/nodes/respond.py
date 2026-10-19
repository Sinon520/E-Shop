"""respond：收口节点。

统一在这里做三件事，保证出口只有一处：
1. 空回答兜底（不能让前端拿到空白气泡）；
2. 转人工意图直接给固定话术，不经过模型（避免它擅自承诺补偿）；
3. 把最终回答写回 messages，供 checkpointer 保存历史。
"""

from __future__ import annotations

import logging

from langchain_core.messages import AIMessage

from ..rules import ESCALATE_ANSWER, FALLBACK
from ..state import ChatState

log = logging.getLogger(__name__)

# 政策类回答里出现这些词，说明用户大概率要去操作，附一个售后入口
AFTER_SALES_WORDS = ("退货", "退款", "换货", "售后", "保修", "维修", "寄回")


def _after_sales_links(state: ChatState, answer: str) -> list[dict]:
    """已登录 + 政策类 + 涉及售后事项时，给一个「我的订单」入口。"""
    if not state.get("logged_in"):
        return []
    if state.get("intent") not in ("policy", "kb"):
        return []
    if not any(word in answer for word in AFTER_SALES_WORDS):
        return []
    return [{"label": "去「我的订单」申请售后", "url": "/orders", "kind": "order"}]


def respond_node(state: ChatState) -> dict:
    intent = state.get("intent", "")
    provider = state.get("provider_used") or "rules"

    if intent == "escalate":
        answer = ESCALATE_ANSWER
    else:
        answer = (state.get("answer") or "").strip() or FALLBACK

    log.info("respond: provider=%s intent=%s", provider, intent)
    return {
        "answer": answer,
        "provider_used": provider,
        # 带上检索出处，否则接入层只能拿到 respond 的增量子集，sources 会丢
        "sources": state.get("sources") or [],
        # 政策类补一个售后入口（links 有 operator.add，会与工具生成的链接合并）
        "links": _after_sales_links(state, answer),
        "messages": [
            AIMessage(
                content=answer,
                additional_kwargs={"provider": provider, "intent": intent},
            )
        ],
    }
