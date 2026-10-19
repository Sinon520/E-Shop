"""guard：身份裁剪节点。

这一版还没有工具，所以只做两件事：
1. 从接入层写入的 user_id 推导登录态；
2. 为后续「未登录时摘掉订单类工具」预留唯一的身份判断点。

红线：user_id 只来自网关注入的 X-User-Id，绝不进入提示词，也不作为模型可填参数。
"""

from __future__ import annotations

import logging

from ..state import ChatState

log = logging.getLogger(__name__)


def guard_node(state: ChatState) -> dict:
    user_id = state.get("user_id")
    logged_in = user_id is not None
    log.info("guard: logged_in=%s user_id=%s", logged_in, user_id)
    return {"logged_in": logged_in}
