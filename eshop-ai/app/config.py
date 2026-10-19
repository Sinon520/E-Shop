"""E-Shop AI 客服服务配置。

所有可调项统一走环境变量（前缀 AI_），密钥放 .env（已 gitignore）。
"""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_FILE = Path(__file__).resolve().parent.parent / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AI_",
        env_file=str(ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "eshop-ai"

    # cloud | local | rules | auto
    # auto: 有云端 key 且可用 → cloud；否则探测本地 Ollama → local；都不行 → rules
    provider: str = "auto"

    cloud_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    cloud_api_key: str = ""
    cloud_chat_model: str = "glm-4-flash"

    local_base_url: str = "http://127.0.0.1:11434/v1"
    local_chat_model: str = "qwen2.5:7b"

    temperature: float = 0.3
    max_tokens: int = 800
    timeout: float = 30.0

    # 会话持久化（P5-2）
    redis_url: str = "redis://127.0.0.1:6379/0"
    checkpoint_ttl: int = 7200  # 秒，滑动续期

    # RAG（P5-3）
    embed_provider: str = "zhipu"  # zhipu（embedding-3, 2048 维）
    embed_model: str = "embedding-3"
    embed_dims: int = 2048
    embed_batch: int = 16
    es_url: str = "http://127.0.0.1:9200"
    kb_index: str = ""  # 留空则按模型自动生成，如 eshop_kb_embedding_3
    kb_chunk_size: int = 400
    kb_chunk_overlap: int = 80
    kb_top_k: int = 3

    # 直连 Java 服务（工具调用），不经网关回环
    java_order_url: str = "http://127.0.0.1:8085"
    java_product_url: str = "http://127.0.0.1:8082"
    java_stock_url: str = "http://127.0.0.1:8083"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
