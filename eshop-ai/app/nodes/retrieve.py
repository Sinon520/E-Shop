"""retrieve：知识库检索节点（P5-3）。

只对政策/售后类问题走检索：订单和商品问题需要实时数据，靠工具而不是知识库。
检索失败不能把回答打挂——降级成「无资料」，由 agent 明确说明并引导转人工。
"""

from __future__ import annotations

import asyncio
import logging

from ..kb.index import hybrid_search
from ..state import ChatState
from ..text import last_user_text

log = logging.getLogger(__name__)


async def retrieve_node(state: ChatState) -> dict:
    query = last_user_text(state).strip()
    if not query:
        return {"context": "", "sources": []}

    try:
        hits = await asyncio.to_thread(hybrid_search, query)
    except Exception as exc:
        log.warning("知识库检索失败（%s），本轮按无资料处理", type(exc).__name__)
        return {"context": "", "sources": []}

    if not hits:
        log.info("retrieve: 无命中")
        return {"context": "", "sources": []}

    log.info(
        "retrieve: top=%s score=%.3f",
        hits[0]["doc"],
        hits[0]["score"],
    )

    return {
        "context": "\n\n".join(hit["content"] for hit in hits),
        "sources": [
            {"title": hit["title"], "doc": hit["doc"], "score": hit["score"]} for hit in hits
        ],
    }
