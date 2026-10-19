"""LangGraph 装配。

当前形态（P5-4）：

    START → guard → router ─┬─(escalate)────────────────────────→ respond → END
                            ├─(policy/kb) → retrieve ──┐
                            └─(order/product) ─────────┴→ agent ⇄ tools
                                                              └→ respond → END

会话检查点：优先 Redis（自研适配器，见 checkpoint.py），Redis 不可用时自动退回内存。
"""

from __future__ import annotations

import logging
from functools import lru_cache

import redis
from langchain_core.messages import AIMessage
from langgraph.graph import END, START, StateGraph

from .checkpoint import RedisCheckpointSaver
from .config import get_settings
from .nodes.agent import agent_node
from .nodes.forced import forced_tool_node
from .nodes.guard import guard_node
from .nodes.respond import respond_node
from .nodes.retrieve import retrieve_node
from .nodes.router import router_node
from .nodes.tools import tools_node
from .state import ChatState
from .text import has_tool_result

try:  # langgraph-checkpoint 4.x 用 InMemorySaver，旧版本叫 MemorySaver
    from langgraph.checkpoint.memory import InMemorySaver as _MemorySaver
except ImportError:  # pragma: no cover - 兼容旧版
    from langgraph.checkpoint.memory import MemorySaver as _MemorySaver

log = logging.getLogger(__name__)


def route_after_router(state: ChatState) -> str:
    """明确要人工的直接收口；政策/未知问题先检索；其余交给 agent。"""
    intent = state.get("intent")
    if intent == "escalate":
        return "respond"
    if intent in ("policy", "kb"):
        return "retrieve"
    return "agent"


def route_after_agent(state: ChatState) -> str:
    """模型要调工具就去 tools；该查没查则走确定性兜底；否则收口。"""
    messages = state.get("messages") or []
    last = messages[-1] if messages else None

    if isinstance(last, AIMessage) and getattr(last, "tool_calls", None):
        return "tools"

    # 商品/订单类却一次工具都没调（模型反问了用户）→ 代码替它查。
    # 订单类必须已登录，否则没有可执行工具，会兜底→再兜底转圈。
    intent = state.get("intent")
    can_fallback = intent == "product" or (intent == "order" and state.get("logged_in"))
    if can_fallback and not has_tool_result(messages):
        return "forced"

    return "respond"


@lru_cache(maxsize=1)
def get_checkpointer():
    """返回 (checkpointer, 类型)。Redis 连不上不阻断启动，退回内存。"""
    settings = get_settings()
    try:
        client = redis.Redis.from_url(
            settings.redis_url,
            socket_connect_timeout=2,
            socket_timeout=3,
            decode_responses=False,
        )
        client.ping()
        log.info("会话检查点：Redis（%s，TTL %ss）", settings.redis_url, settings.checkpoint_ttl)
        return RedisCheckpointSaver(client, ttl=settings.checkpoint_ttl)
    except Exception as exc:
        log.warning("Redis 不可用（%s），会话退回内存检查点（重启即丢）", type(exc).__name__)
        return _MemorySaver()


def checkpointer_kind() -> str:
    return type(get_checkpointer()).__name__


def history_count(thread_id: str) -> int:
    """当前会话已存的历史消息条数；内存检查点不提供，返回 0。"""
    counter = getattr(get_checkpointer(), "count_messages", None)
    if counter is None:
        return 0
    try:
        return int(counter(thread_id))
    except Exception:
        return 0


@lru_cache(maxsize=1)
def get_graph():
    builder = StateGraph(ChatState)

    builder.add_node("guard", guard_node)
    builder.add_node("router", router_node)
    builder.add_node("retrieve", retrieve_node)
    builder.add_node("agent", agent_node)
    builder.add_node("tools", tools_node)
    builder.add_node("forced", forced_tool_node)
    builder.add_node("respond", respond_node)

    builder.add_edge(START, "guard")
    builder.add_edge("guard", "router")
    builder.add_conditional_edges(
        "router",
        route_after_router,
        {"agent": "agent", "retrieve": "retrieve", "respond": "respond"},
    )
    builder.add_edge("retrieve", "agent")
    builder.add_conditional_edges(
        "agent",
        route_after_agent,
        {"tools": "tools", "forced": "forced", "respond": "respond"},
    )
    builder.add_edge("tools", "agent")
    builder.add_edge("forced", "agent")
    builder.add_edge("respond", END)

    return builder.compile(checkpointer=get_checkpointer())
