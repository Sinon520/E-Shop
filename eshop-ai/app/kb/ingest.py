"""知识库灌库脚本。

用法（在 eshop-ai 目录下）：
    python -m app.kb.ingest            # 增量：索引存在则复用，按 chunk_id 覆盖
    python -m app.kb.ingest --rebuild  # 重建：删索引后重新切分、向量化、写入

切分策略：按标题行取 title，正文按句末标点切句后打包成 ~400 字的块，
相邻块保留 80 字重叠，避免答案正好被切在边界上。
"""

from __future__ import annotations

import argparse
import logging
import re
import sys
from pathlib import Path

from ..config import get_settings
from .embedder import EmbeddingError, get_embedder
from .index import bulk_index, ensure_index, index_name

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
log = logging.getLogger("kb.ingest")

KB_DIR = Path(__file__).resolve().parent.parent.parent / "data" / "kb"


def split_text(text: str, size: int, overlap: int) -> list[str]:
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    # 在句号 / 问号 / 感叹号 / 分号 / 换行后切句，保留标点
    parts = re.split(r"(?<=[。！？；\n])", text)
    chunks: list[str] = []
    buf = ""

    for part in parts:
        part = part.strip()
        if not part:
            continue

        if len(buf) + len(part) <= size:
            buf += part
            continue

        if buf:
            chunks.append(buf.strip())
            carry = chunks[-1][-overlap:] if overlap else ""
            buf = carry + part
        else:
            buf = part

        # 单句本身超长时硬切
        while len(buf) > size:
            chunks.append(buf[:size].strip())
            buf = (buf[size - overlap :] if overlap else buf[size:])

    if buf.strip():
        chunks.append(buf.strip())

    return chunks


def load_docs() -> list[dict]:
    docs = []
    for path in sorted(KB_DIR.glob("*.md")):
        raw = path.read_text(encoding="utf-8")
        lines = raw.splitlines()
        title = next((l.lstrip("# ").strip() for l in lines if l.startswith("# ")), path.stem)
        body = "\n".join(l for l in lines if not l.startswith("# ")).strip()
        docs.append({"doc": path.stem, "title": title, "body": body})
    return docs


def main() -> int:
    parser = argparse.ArgumentParser(description="E-Shop 知识库灌库")
    parser.add_argument("--rebuild", action="store_true", help="删除并重建索引")
    args = parser.parse_args()

    settings = get_settings()
    if not KB_DIR.exists():
        log.error("知识库目录不存在：%s", KB_DIR)
        return 1

    docs = load_docs()
    if not docs:
        log.error("知识库为空：%s", KB_DIR)
        return 1

    rows: list[dict] = []
    for doc in docs:
        chunks = split_text(doc["body"], settings.kb_chunk_size, settings.kb_chunk_overlap)
        for i, chunk in enumerate(chunks):
            # 去掉 markdown 标题符号，避免模型把「##」原样抄进答案和引用里
            clean = re.sub(r"#{1,6}\s*", "", chunk).strip()
            rows.append(
                {
                    "chunk_id": f"{doc['doc']}-{i:03d}",
                    "doc": doc["doc"],
                    "title": doc["title"],
                    # 标题拼进正文，让检索和回答都能带上出处
                    "content": f"【{doc['title']}】{clean}",
                    "source_text": clean,
                }
            )
        log.info("%-22s %d 块", doc["doc"], len(chunks))

    log.info("共 %d 篇 / %d 块，开始向量化（model=%s）", len(docs), len(rows), settings.embed_model)

    try:
        embedder = get_embedder()
        vectors = embedder.embed([row["source_text"] for row in rows])
    except EmbeddingError as exc:
        log.error("向量化失败：%s", exc)
        return 2

    for row, vector in zip(rows, vectors):
        row["vector"] = vector

    rebuilt = ensure_index(rebuild=args.rebuild)
    written = bulk_index(rows)

    log.info(
        "完成：索引 %s%s，写入 %d 块",
        index_name(),
        "（新建）" if rebuilt else "（复用）",
        written,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
