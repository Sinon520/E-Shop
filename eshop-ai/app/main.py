"""FastAPI 接入层。

约定与 Java 侧保持一致：业务响应一律 HTTP 200 + {code, message, data}；
流式接口用 SSE（text/event-stream），事件体同样是 JSON。

SSE 事件协议（P5-2）：
    meta   {sessionId, provider, checkpointer, historyCount}   —— 流的第一个事件
    status {text}                                              —— 节点中间态
    delta  {text}                                              —— token 增量
    done   {answer, intent, provider, sources, historyCount}    —— 收尾，含完整回答
    error  {message}                                           —— 生成失败兜底
"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse
from langchain_core.messages import HumanMessage
from pydantic import BaseModel, ConfigDict, Field

from . import __version__
from .graph import checkpointer_kind, get_graph, history_count
from .llm import provider_name, resolve_provider

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("eshop-ai")

STATUS_TEXT = {
    "order": "正在查询订单相关信息…",
    "product": "正在为你查找商品…",
    "policy": "正在查阅售后规则…",
    "kb": "正在查阅知识库…",
    "escalate": "正在为你转接人工…",
    "chitchat": "正在思考…",
    "default": "正在思考…",
}


@asynccontextmanager
async def lifespan(_: FastAPI):
    name, _model = resolve_provider()
    log.info("eshop-ai %s 启动完成，模型通道=%s，会话存储=%s", __version__, name, checkpointer_kind())
    yield


app = FastAPI(
    title="E-Shop AI 客服",
    version=__version__,
    description="RAG + 工具调用的商城智能客服（P5）",
    lifespan=lifespan,
)


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1, max_length=2000)
    session_id: str | None = Field(default=None, alias="sessionId")


def _ok(data: dict) -> dict:
    return {"code": 200, "message": "ok", "data": data}


def _sse(payload: dict) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _user_id_from(request: Request) -> int | None:
    """身份只认网关注入的 X-User-Id；拿不到就当游客。"""
    raw = request.headers.get("X-User-Id", "").strip()
    return int(raw) if raw.isdigit() else None


def _resolve_session(raw: str | None) -> str:
    return (raw or "").strip() or f"guest-{uuid.uuid4().hex[:12]}"


def _initial_state(message: str, user_id: int | None) -> dict:
    return {
        "messages": [HumanMessage(content=message)],
        "user_id": user_id,
        "intent": "",
        "answer": "",
        "provider_used": "",
        "sources": [],
        "links": [],
        "tools_used": [],
    }


def _dedup_links(items: list[dict], limit: int = 6) -> list[dict]:
    seen: set[str] = set()
    out: list[dict] = []
    for item in items:
        url = item.get("url")
        if not url or url in seen:
            continue
        seen.add(url)
        out.append(item)
        if len(out) >= limit:
            break
    return out


def _run_config(session_id: str) -> dict:
    return {
        "configurable": {"thread_id": session_id},
        # 工具循环会多走几步（agent→tools→agent），留足余量又防止死循环
        "recursion_limit": 12,
    }


@app.get("/health")
def health() -> dict:
    return _ok(
        {
            "status": "UP",
            "version": __version__,
            "provider": provider_name(),
            "checkpointer": checkpointer_kind(),
        }
    )


@app.post("/chat")
async def chat(req: ChatRequest, request: Request) -> dict:
    """非流式对话（保留给脚本与快速验证）。"""
    user_id = _user_id_from(request)
    session_id = _resolve_session(req.session_id)
    before = history_count(session_id)

    result = await get_graph().ainvoke(
        _initial_state(req.message, user_id),
        _run_config(session_id),
    )

    log.info(
        "chat: session=%s user=%s intent=%s provider=%s",
        session_id,
        user_id,
        result.get("intent"),
        result.get("provider_used"),
    )

    return _ok(
        {
            "sessionId": session_id,
            "answer": result.get("answer", ""),
            "intent": result.get("intent", ""),
            "provider": result.get("provider_used", ""),
            "loggedIn": user_id is not None,
            "sources": result.get("sources", []),
            "historyCount": before,
            "links": _dedup_links(result.get("links") or []),
            "toolsUsed": sorted(set(result.get("tools_used") or [])),
        }
    )


@app.post("/chat/stream")
async def chat_stream(req: ChatRequest, request: Request) -> StreamingResponse:
    user_id = _user_id_from(request)
    session_id = _resolve_session(req.session_id)
    initial = _initial_state(req.message, user_id)
    config = _run_config(session_id)

    async def event_stream():
        yield _sse(
            {
                "type": "meta",
                "sessionId": session_id,
                "provider": provider_name(),
                "checkpointer": checkpointer_kind(),
                "historyCount": history_count(session_id),
            }
        )

        answer = ""
        intent = ""
        provider = ""
        sources: list = []
        links: list[dict] = []
        tools_used: list[str] = []

        try:
            async for mode, payload in get_graph().astream(
                initial, config, stream_mode=["custom", "updates"]
            ):
                if mode == "custom":
                    if isinstance(payload, dict):
                        kind = payload.get("type")
                        text = payload.get("text", "")
                        if kind == "delta" and text:
                            yield _sse({"type": "delta", "text": text})
                        elif kind == "status" and text:
                            yield _sse({"type": "status", "text": text})
                    continue

                for node, update in (payload or {}).items():
                    if not isinstance(update, dict):
                        continue
                    # 任何节点产出的链接都收进来（tools 的商品/订单、respond 的售后入口）
                    if update.get("links"):
                        links.extend(update["links"])
                    if update.get("tools_used"):
                        tools_used.extend(update["tools_used"])
                    if node == "router":
                        intent = update.get("intent", "")
                        yield _sse(
                            {
                                "type": "status",
                                "text": STATUS_TEXT.get(intent, STATUS_TEXT["default"]),
                            }
                        )
                    elif node == "respond":
                        answer = update.get("answer", "")
                        provider = update.get("provider_used", "")
                        sources = update.get("sources", []) or []

            log.info("chat/stream: session=%s user=%s intent=%s provider=%s", session_id, user_id, intent, provider)
            yield _sse(
                {
                    "type": "done",
                    "answer": answer,
                    "intent": intent,
                    "provider": provider,
                    "sources": sources,
                    "loggedIn": user_id is not None,
                    "historyCount": history_count(session_id),
                    "links": _dedup_links(links),
                    "toolsUsed": sorted(set(tools_used)),
                }
            )

        except asyncio.CancelledError:
            # 用户点了「停止生成」或断开连接：图已中止，不再写完成事件
            log.info("chat/stream 被取消: session=%s", session_id)
            raise
        except Exception as exc:
            log.exception("chat/stream 失败: %s", exc)
            yield _sse({"type": "error", "message": "客服暂时不可用，请稍后再试"})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.exception_handler(Exception)
async def unhandled(_: Request, exc: Exception) -> JSONResponse:  # pragma: no cover
    log.exception("未处理异常: %s", exc)
    return JSONResponse(
        status_code=200,
        content={"code": 7001, "message": "客服服务暂时不可用，请稍后再试", "data": None},
    )
