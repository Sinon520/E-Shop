"""ES 向量索引：建表、灌库、混合检索。

为什么复用 ES 而不是引 Milvus/Qdrant：
    项目已经有 ES 8.18（商品搜索在用），再多一套向量库等于多一份运维成本；
    本知识库规模只有几十个 chunk，kNN 完全够用。
    顺带还能把「向量召回 + BM25 词召回」做成混合检索，单独一个向量库反而做不到。

混合策略：两路各取 top 3k，分别按本路最高分归一化后加权融合（向量 0.7 / 词 0.3）。
不用 ES 8.12+ 的 RRF，是因为加权融合的权重可解释、可调，面试也好讲。
"""

from __future__ import annotations

import logging
from functools import lru_cache

from elasticsearch import Elasticsearch, helpers

from ..config import get_settings
from ..text import normalize_for_bm25
from .embedder import get_embedder

log = logging.getLogger(__name__)

VECTOR_WEIGHT = 0.7
TEXT_WEIGHT = 0.3


def index_name() -> str:
    s = get_settings()
    if s.kb_index:
        return s.kb_index
    return "eshop_kb_" + s.embed_model.replace(".", "_").replace("-", "_")


@lru_cache(maxsize=1)
def get_client() -> Elasticsearch:
    return Elasticsearch(get_settings().es_url, request_timeout=10, max_retries=1)


def _mappings(analyzer: str) -> dict:
    return {
        "settings": {"number_of_shards": 1, "number_of_replicas": 0},
        "mappings": {
            "properties": {
                "chunk_id": {"type": "keyword"},
                "doc": {"type": "keyword"},
                "title": {
                    "type": "text",
                    "analyzer": analyzer,
                    "search_analyzer": analyzer,
                },
                "content": {
                    "type": "text",
                    "analyzer": analyzer,
                    "search_analyzer": analyzer,
                },
                "vector": {
                    "type": "dense_vector",
                    "dims": get_settings().embed_dims,
                    "index": True,
                    "similarity": "cosine",
                },
            }
        },
    }


def ensure_index(rebuild: bool = False) -> bool:
    """返回 True 表示本次新建了索引。"""
    client = get_client()
    name = index_name()

    if client.indices.exists(index=name):
        if not rebuild:
            return False
        client.indices.delete(index=name)
        log.info("已删除旧索引 %s", name)

    try:
        client.indices.create(index=name, **_mappings("ik_max_word"))
        log.info("索引 %s 创建完成（IK 分词）", name)
    except Exception as exc:
        log.warning("IK 分词不可用（%s），改用 standard 分词", type(exc).__name__)
        client.indices.create(index=name, **_mappings("standard"))
        log.info("索引 %s 创建完成（standard 分词）", name)
    return True


def bulk_index(rows: list[dict]) -> int:
    if not rows:
        return 0
    client = get_client()
    actions = [
        {"_index": index_name(), "_id": row["chunk_id"], "_source": row}
        for row in rows
    ]
    ok, errors = helpers.bulk(client, actions, stats_only=False, raise_on_error=False)
    if errors:
        log.warning("批量写入有 %d 条失败，首条：%s", len(errors), errors[0])
    client.indices.refresh(index=index_name())
    return ok


def _hit_map(resp) -> dict[str, tuple[float, dict]]:
    out: dict[str, tuple[float, dict]] = {}
    for hit in resp["hits"]["hits"]:
        out[hit["_id"]] = (float(hit.get("_score") or 0.0), hit["_source"])
    return out


def _normalize(scores: dict[str, tuple[float, dict]]) -> dict[str, float]:
    if not scores:
        return {}
    top = max(score for score, _ in scores.values()) or 1.0
    return {key: score / top for key, (score, _) in scores.items()}


def hybrid_search(query: str, top_k: int | None = None) -> list[dict]:
    """向量召回 + BM25 召回，归一化加权融合。索引不存在时抛异常，由调用方降级。"""
    settings = get_settings()
    k = top_k or settings.kb_top_k
    size = max(k * 3, 10)

    vector = get_embedder().embed_query(query)
    client = get_client()

    knn_resp = client.search(
        index=index_name(),
        knn={
            "field": "vector",
            "query_vector": vector,
            "k": size,
            "num_candidates": max(50, size * 5),
        },
        size=size,
    )
    text_resp = client.search(
        index=index_name(),
        query={"multi_match": {"query": normalize_for_bm25(query), "fields": ["title^2", "content"]}},
        size=size,
    )

    knn_hits = _hit_map(knn_resp)
    text_hits = _hit_map(text_resp)
    knn_norm = _normalize(knn_hits)
    text_norm = _normalize(text_hits)

    fused: dict[str, float] = {}
    for key in set(knn_norm) | set(text_norm):
        fused[key] = VECTOR_WEIGHT * knn_norm.get(key, 0.0) + TEXT_WEIGHT * text_norm.get(key, 0.0)

    results = []
    for key, score in sorted(fused.items(), key=lambda kv: kv[1], reverse=True)[:k]:
        source = (knn_hits.get(key) or text_hits.get(key))[1]
        results.append(
            {
                "chunk_id": key,
                "doc": source.get("doc", ""),
                "title": source.get("title", ""),
                "content": source.get("content", ""),
                "score": round(score, 4),
                "vector_score": round(knn_norm.get(key, 0.0), 4),
                "text_score": round(text_norm.get(key, 0.0), 4),
            }
        )
    return results


def stats() -> dict:
    try:
        client = get_client()
        if not client.indices.exists(index=index_name()):
            return {"index": index_name(), "ready": False, "chunks": 0}
        count = client.count(index=index_name())
        return {"index": index_name(), "ready": True, "chunks": int(count["count"])}
    except Exception as exc:
        return {"index": index_name(), "ready": False, "error": type(exc).__name__}
