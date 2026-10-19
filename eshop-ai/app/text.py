"""节点间共用的小工具（避免每个节点各写一份）。"""

from __future__ import annotations

from typing import Any

from langchain_core.messages import HumanMessage, ToolMessage

# 中文疑问句里的这些词对 BM25 是纯噪声，也会干扰关键词抽取：
# FAQ 文档里全是「怎么办」「可以…吗」，不清理的话相关度会被虚词带偏。
QUESTION_NOISE = (
    "怎么办", "怎么样", "怎么", "怎样", "如何", "能不能", "可不可以", "可以",
    "能否", "是否", "是不是", "请问", "想问", "问一下", "一下",
    "吗", "呢", "呀", "啊", "嘛",
)


def normalize_for_bm25(text: str) -> str:
    """去掉疑问词/语气词。检索和关键词抽取共用。"""
    out = text or ""
    for word in QUESTION_NOISE:
        out = out.replace(word, "")
    out = out.strip(" ，。！？、,.?!")
    return out or (text or "")


def has_tool_result(messages: list) -> bool:
    """本轮（最后一条 HumanMessage 之后）是否已经有工具结果。"""
    for msg in reversed(messages or []):
        if isinstance(msg, ToolMessage):
            return True
        if isinstance(msg, HumanMessage):
            return False
    return False


def last_user_text(state: dict) -> str:
    for msg in reversed(state.get("messages") or []):
        if isinstance(msg, HumanMessage):
            return str(msg.content)
    return ""


def normalize_content(content: Any) -> str:
    """兼容部分模型返回 content 为分块列表的情况。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, dict) and item.get("type") == "text":
                parts.append(str(item.get("text", "")))
            else:
                parts.append(str(item))
        return "".join(parts)
    return str(content)
