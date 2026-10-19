"""LangGraph 会话状态。

user_id 只来自网关注入的 X-User-Id，**永远不作为工具参数暴露给模型**（见设计文档 §6 红线）。
"""

from __future__ import annotations

import operator
from typing import Annotated, TypedDict

from langchain_core.messages import AnyMessage
from langgraph.graph.message import add_messages


class ChatState(TypedDict, total=False):
    # 对话历史：add_messages 负责追加与按 id 去重
    messages: Annotated[list[AnyMessage], add_messages]

    # 身份：由接入层从请求头解析，模型不可见
    user_id: int | None
    logged_in: bool

    # 路由结果：policy | order | product | chitchat | escalate
    intent: str

    # RAG 检索结果（P5-3）：拼好的参考资料 + 可展示的出处
    context: str
    sources: list[dict]

    # 本轮产出
    answer: str
    provider_used: str

    # 前端可点的跳转链接（工具侧生成，多轮工具调用用 operator.add 合并）
    links: Annotated[list[dict], operator.add]

    # 本轮实际调用过的工具名（评测与排查用）
    tools_used: Annotated[list[str], operator.add]
