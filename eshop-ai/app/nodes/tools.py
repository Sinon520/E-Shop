"""tools 节点：执行模型请求的工具。

工具集合按登录态动态构建（未登录时没有订单类工具），
所以不能用 `builder.add_node("tools", ToolNode(tools))` 固定绑定，
这里做一个薄包装，每次按 state 重建。
"""

from __future__ import annotations

import logging

from langgraph.config import get_stream_writer
from langgraph.prebuilt import ToolNode

from ..state import ChatState
from ..tools import build_tools

log = logging.getLogger(__name__)

ORDER_TOOL_NAMES = {"query_my_orders", "get_order_detail"}


def _pending_tool_names(state: ChatState) -> set[str]:
    messages = state.get("messages") or []
    if not messages:
        return set()
    calls = getattr(messages[-1], "tool_calls", None) or []
    return {call.get("name") for call in calls if call.get("name")}


async def tools_node(state: ChatState) -> dict:
    sink: list[dict] = []
    tools = build_tools(state.get("user_id"), bool(state.get("logged_in")), sink=sink)
    names = _pending_tool_names(state)

    label = "正在查询你的订单…" if names & ORDER_TOOL_NAMES else "正在为你查询商品信息…"
    try:
        get_stream_writer()({"type": "status", "text": label})
    except Exception:
        pass

    log.info("tools: 执行 %s", sorted(names))
    result = await ToolNode(tools).ainvoke(state)
    if sink:
        result["links"] = sink
    if names:
        result["tools_used"] = sorted(names)
    return result
