"""基于原生 Redis 的检查点实现。

为什么不用 `langgraph-checkpoint-redis`：
    它依赖 RedisJSON + RediSearch（要执行 `JSON.SET` / `FT.CREATE`），
    而本机是 Redis 社区版（实测 `JSON.SET`、`FT._LIST` 都是 unknown command）。
    与其为了一个检查点再装 Redis Stack，不如按 `BaseCheckpointSaver` 实现一个
    只依赖 String/Hash 的版本——序列化仍交给 LangGraph 自带的 serde，
    所以消息对象、channel_versions 这些复杂结构不需要我们自己处理。

存储结构（每个 thread 一个 Hash，TTL 滑动续期）：
    eshop:ai:ckpt:{thread_id}:{ns}
      type -> json   （serde 的编码类型）
      data -> bytes  （checkpoint + metadata + parent + pending_writes 的序列化结果）

只保留「最新一份」检查点：单机演示场景不需要时间旅行，换取实现简单与体积可控。
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Iterator, Sequence

import redis
from langchain_core.runnables import RunnableConfig
from langgraph.checkpoint.base import (
    BaseCheckpointSaver,
    Checkpoint,
    CheckpointMetadata,
    CheckpointTuple,
)

log = logging.getLogger(__name__)


class RedisCheckpointSaver(BaseCheckpointSaver):
    def __init__(self, client: redis.Redis, prefix: str = "eshop:ai:ckpt", ttl: int = 7200) -> None:
        super().__init__()
        self.client = client
        self.prefix = prefix
        self.ttl = ttl

    # ---------- 内部 ----------
    def _key(self, thread_id: str, checkpoint_ns: str) -> str:
        return f"{self.prefix}:{thread_id}:{checkpoint_ns or '-'}"

    def _load(self, key: str) -> dict | None:
        raw = self.client.hgetall(key)
        if not raw or b"data" not in raw:
            return None
        try:
            return self.serde.loads_typed((raw[b"type"].decode(), raw[b"data"]))
        except Exception as exc:  # 版本升级后旧数据可能反序列化失败，按无历史处理
            log.warning("检查点反序列化失败（%s），按无历史处理", type(exc).__name__)
            return None

    def _save(self, key: str, payload: dict) -> None:
        type_name, data = self.serde.dumps_typed(payload)
        pipe = self.client.pipeline()
        pipe.hset(key, mapping={"type": type_name, "data": data})
        pipe.expire(key, self.ttl)
        pipe.execute()

    # ---------- 同步接口 ----------
    def get_tuple(self, config: RunnableConfig) -> CheckpointTuple | None:
        conf = config.get("configurable") or {}
        thread_id = conf.get("thread_id")
        if not thread_id:
            return None

        checkpoint_ns = conf.get("checkpoint_ns", "")
        payload = self._load(self._key(thread_id, checkpoint_ns))
        if not payload:
            return None

        checkpoint = payload.get("checkpoint") or {}
        current_id = checkpoint.get("id")
        wanted_id = conf.get("checkpoint_id")
        # 只保留最新一份，历史版本一律视为不存在
        if wanted_id and wanted_id != current_id:
            return None

        return CheckpointTuple(
            config={
                "configurable": {
                    "thread_id": thread_id,
                    "checkpoint_ns": checkpoint_ns,
                    "checkpoint_id": current_id,
                }
            },
            checkpoint=checkpoint,
            metadata=payload.get("metadata") or {},
            parent_config=payload.get("parent_config"),
            pending_writes=[tuple(w) for w in payload.get("pending_writes") or []],
        )

    def put(
        self,
        config: RunnableConfig,
        checkpoint: Checkpoint,
        metadata: CheckpointMetadata,
        new_versions: dict[str, Any],
    ) -> RunnableConfig:
        conf = config["configurable"]
        thread_id = conf["thread_id"]
        checkpoint_ns = conf.get("checkpoint_ns", "")
        key = self._key(thread_id, checkpoint_ns)

        parent_id = conf.get("checkpoint_id")
        previous = self._load(key) or {}
        pending_writes = previous.get("pending_writes") or []
        # 换到新检查点时，上一轮残留的 pending writes 不再有意义
        if (previous.get("checkpoint") or {}).get("id") != checkpoint.get("id"):
            pending_writes = []

        # 注意：config["configurable"] 里混着 __pregel_runtime 等内部对象，
        # 整包序列化会报 "Type is not msgpack serializable: Runtime"。
        # 落盘只保留这三个可序列化的字段。
        parent_config = (
            {
                "configurable": {
                    "thread_id": thread_id,
                    "checkpoint_ns": checkpoint_ns,
                    "checkpoint_id": parent_id,
                }
            }
            if parent_id
            else None
        )

        self._save(
            key,
            {
                "checkpoint": checkpoint,
                "metadata": metadata,
                "parent_config": parent_config,
                "pending_writes": pending_writes,
            },
        )
        return {"configurable": {**conf, "checkpoint_id": checkpoint.get("id")}}

    def put_writes(
        self,
        config: RunnableConfig,
        writes: Sequence[tuple[str, Any]],
        task_id: str,
        task_path: str = "",
    ) -> None:
        conf = config["configurable"]
        key = self._key(conf["thread_id"], conf.get("checkpoint_ns", ""))
        payload = self._load(key)
        if not payload:
            return
        payload.setdefault("pending_writes", [])
        payload["pending_writes"].extend([[task_id, channel, value] for channel, value in writes])
        self._save(key, payload)

    def list(
        self,
        config: RunnableConfig | None,
        *,
        filter: dict[str, Any] | None = None,
        before: RunnableConfig | None = None,
        limit: int | None = None,
    ) -> Iterator[CheckpointTuple]:
        if config is None:
            return iter(())
        item = self.get_tuple(config)
        return iter([item] if item else [])

    # ---------- 异步包装（图用 ainvoke/astream，必须提供） ----------
    async def aget_tuple(self, config: RunnableConfig) -> CheckpointTuple | None:
        return await asyncio.to_thread(self.get_tuple, config)

    async def aput(
        self,
        config: RunnableConfig,
        checkpoint: Checkpoint,
        metadata: CheckpointMetadata,
        new_versions: dict[str, Any],
    ) -> RunnableConfig:
        return await asyncio.to_thread(self.put, config, checkpoint, metadata, new_versions)

    async def aput_writes(
        self,
        config: RunnableConfig,
        writes: Sequence[tuple[str, Any]],
        task_id: str,
        task_path: str = "",
    ) -> None:
        await asyncio.to_thread(self.put_writes, config, writes, task_id, task_path)

    async def alist(
        self,
        config: RunnableConfig | None,
        *,
        filter: dict[str, Any] | None = None,
        before: RunnableConfig | None = None,
        limit: int | None = None,
    ):
        for item in self.list(config, filter=filter, before=before, limit=limit):
            yield item

    # ---------- 运维辅助 ----------
    def count_messages(self, thread_id: str, checkpoint_ns: str = "") -> int:
        """读取当前会话历史条数，用于 /chat 返回 historyCount。"""
        payload = self._load(self._key(thread_id, checkpoint_ns))
        if not payload:
            return 0
        values = (payload.get("checkpoint") or {}).get("channel_values") or {}
        return len(values.get("messages") or [])

    def drop_thread(self, thread_id: str, checkpoint_ns: str = "") -> None:
        self.client.delete(self._key(thread_id, checkpoint_ns))
