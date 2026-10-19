"""forced：确定性兜底工具节点。

为什么需要它：实测 glm-4-flash 对 `tool_choice="required"` 并不总是遵守——
「运动鞋有货吗」「我的订单到哪了」这类短问句，它会反问用户"想了解哪个品牌"
而不是去查数据。只靠提示词或 tool_choice 都堵不住。

这里不跟模型商量：直接从提问里抽关键词/订单号，替它把工具调了，
把结果作为 ToolMessage 回灌，下一轮 agent 只负责组织语言。

分工依然是清晰的——**代码决定查什么，模型决定怎么说**。
"""

from __future__ import annotations

import logging
import uuid

from langchain_core.messages import AIMessage, ToolMessage

from ..state import ChatState
from ..text import last_user_text
from ..tools import build_tools, extract_order_no, extract_product_query

log = logging.getLogger(__name__)


async def forced_tool_node(state: ChatState) -> dict:
    text = last_user_text(state)
    sink: list[dict] = []
    tools = {
        tool.name: tool
        for tool in build_tools(state.get("user_id"), bool(state.get("logged_in")), sink=sink)
    }
    intent = state.get("intent")

    if intent == "order":
        order_no = extract_order_no(text)
        if order_no and "get_order_detail" in tools:
            name, args = "get_order_detail", {"order_no": order_no}
        elif "query_my_orders" in tools:
            name, args = "query_my_orders", {}
        else:
            log.info("forced: 未登录且无订单工具，交给模型引导登录")
            return {}
    else:
        keyword, max_price = extract_product_query(text)
        name, args = "search_products", {"keyword": keyword, "max_price": max_price}

    tool = tools.get(name)
    if tool is None:
        return {}

    try:
        result = await tool.ainvoke(args)
    except Exception as exc:
        log.warning("forced: %s 执行失败（%s）", name, type(exc).__name__)
        return {}

    call_id = f"forced-{uuid.uuid4().hex[:8]}"
    log.info("forced: 模型未调工具，确定性执行 %s(%s)", name, args)

    return {
        "messages": [
            AIMessage(content="", tool_calls=[{"name": name, "args": args, "id": call_id}]),
            ToolMessage(content=str(result), tool_call_id=call_id, name=name),
        ],
        "links": sink,
        "tools_used": [name],
    }
