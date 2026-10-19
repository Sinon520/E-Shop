"""模型工厂：按配置解析出可用的模型通道。

三级降级（docs/ai-customer-service.md §7.3）：
    cloud → local → rules
返回 (provider_name, chat_model)。rules 模式下 chat_model 为 None，
由 agent 节点走关键词 FAQ 兜底，保证没有 API Key 也能正常应答。
"""

from functools import lru_cache
from typing import Any

import httpx
from langchain_openai import ChatOpenAI

from .config import get_settings

_PROBE_TIMEOUT = 1.5


def _build(base_url: str, api_key: str, model: str) -> ChatOpenAI:
    s = get_settings()
    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=s.temperature,
        max_tokens=s.max_tokens,
        timeout=s.timeout,
        # 默认会重试 3 次，云端故障时用户要等 6s+ 才看到兜底回答；降级场景 1 次足够
        max_retries=1,
    )


def _ollama_alive(base_url: str) -> bool:
    """本地 Ollama 是否在线（/v1/models 是 OpenAI 兼容端点，最轻量）。"""
    try:
        resp = httpx.get(f"{base_url.rstrip('/')}/models", timeout=_PROBE_TIMEOUT)
        return resp.status_code == 200
    except Exception:
        return False


@lru_cache(maxsize=1)
def resolve_provider() -> tuple[str, Any | None]:
    s = get_settings()
    provider = s.provider.strip().lower()

    if provider in ("cloud", "auto") and s.cloud_api_key.strip():
        return "cloud", _build(s.cloud_base_url, s.cloud_api_key, s.cloud_chat_model)

    if provider in ("local", "auto") and _ollama_alive(s.local_base_url):
        return "local", _build(s.local_base_url, "ollama", s.local_chat_model)

    return "rules", None


def provider_name() -> str:
    return resolve_provider()[0]
