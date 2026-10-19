"""Embedding：文本 → 向量。

后端固定走智谱 `embedding-3`（2048 维），与 chat 模型共用同一个 API Key。
带磁盘缓存：同一段文本只计费一次，改代码反复重建索引不会重复花钱。
"""

from __future__ import annotations

import hashlib
import json
import logging
from pathlib import Path

from openai import OpenAI

from ..config import get_settings

log = logging.getLogger(__name__)

CACHE_PATH = Path(__file__).resolve().parent.parent.parent / "data" / "embed_cache.json"


class EmbeddingError(RuntimeError):
    pass


class Embedder:
    def __init__(self) -> None:
        s = get_settings()
        if not s.cloud_api_key:
            raise EmbeddingError("缺少 AI_CLOUD_API_KEY，无法调用 embedding 接口")
        self.model = s.embed_model
        self.batch = s.embed_batch
        self.dims = s.embed_dims
        self.client = OpenAI(api_key=s.cloud_api_key, base_url=s.cloud_base_url, timeout=30)
        self._cache: dict[str, list[float]] = {}
        self._dirty = False
        self._load_cache()

    # ---------- 缓存 ----------
    def _load_cache(self) -> None:
        if not CACHE_PATH.exists():
            return
        try:
            self._cache = json.loads(CACHE_PATH.read_text(encoding="utf-8"))
            log.info("embedding 缓存已加载：%d 条", len(self._cache))
        except Exception:
            self._cache = {}

    def save_cache(self) -> None:
        if not self._dirty:
            return
        CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
        CACHE_PATH.write_text(json.dumps(self._cache), encoding="utf-8")
        self._dirty = False

    def _key(self, text: str) -> str:
        return hashlib.sha256(f"{self.model}:{text}".encode("utf-8")).hexdigest()

    # ---------- 对外 ----------
    def embed(self, texts: list[str]) -> list[list[float]]:
        """批量向量化；命中缓存的不再请求接口。"""
        result: list[list[float] | None] = [None] * len(texts)
        pending: list[tuple[int, str]] = []

        for i, text in enumerate(texts):
            cached = self._cache.get(self._key(text))
            if cached:
                result[i] = cached
            else:
                pending.append((i, text))

        for start in range(0, len(pending), self.batch):
            part = pending[start : start + self.batch]
            try:
                resp = self.client.embeddings.create(
                    model=self.model,
                    input=[text for _, text in part],
                )
            except Exception as exc:
                raise EmbeddingError(f"embedding 调用失败：{type(exc).__name__}") from exc

            for (idx, text), item in zip(part, resp.data):
                vector = list(item.embedding)
                if len(vector) != self.dims:
                    raise EmbeddingError(
                        f"向量维度不符：期望 {self.dims}，实际 {len(vector)}（检查 AI_EMBED_DIMS）"
                    )
                result[idx] = vector
                self._cache[self._key(text)] = vector
                self._dirty = True

        self.save_cache()
        return [vec for vec in result if vec is not None]

    def embed_query(self, text: str) -> list[float]:
        return self.embed([text])[0]


_embedder: Embedder | None = None


def get_embedder() -> Embedder:
    global _embedder
    if _embedder is None:
        _embedder = Embedder()
    return _embedder
