"""router：意图路由节点。

先用关键词规则判：零延迟、可解释、可测试；命中不了再兜底。

意图取值：
    escalate  明确要人工
    order     订单/物流（P5-4 接工具）
    policy    政策/售后（走知识库检索）
    product   商品咨询（P5-4 接工具）
    chitchat  打招呼、跑题
    kb        认不出但像正经问题的（**默认走检索**，不要掉进闲聊分支）
"""

from __future__ import annotations

import logging

from ..state import ChatState
from ..text import last_user_text

log = logging.getLogger(__name__)

# 按优先级排列：越靠前越优先命中
INTENTS: list[tuple[str, tuple[str, ...]]] = [
    ("escalate", ("人工", "转人工", "投诉", "客服电话", "找客服", "升级投诉")),
    ("order", ("订单", "物流", "快递", "单号", "到哪", "签收", "配送", "发货")),
    (
        "policy",
        (
            "退", "换货", "运费", "发票", "支付", "付款", "保修", "售后", "政策",
            "七天", "包邮", "保价", "价保", "降价", "价格保护", "三包", "维修",
            "账号", "密码", "登录", "注册", "收货地址", "优惠券", "缺货", "描述不符",
            "退款",
        ),
    ),
    ("product", ("多少钱", "价格", "有货", "库存", "尺码", "颜色", "推荐", "型号", "适合")),
]

# 时效类问句优先判为政策：这类问题要查知识库，不该去调订单工具。
# 例："多久发货" 命中 order 的「发货」关键词，但用户问的是规则而不是自己的订单。
DELIVERY_TIME_HINTS = (
    "多久发", "什么时候发", "几天发", "多久到", "什么时候到", "多久能到",
    "几天到", "多久能发货", "什么时候能发货",
)

# 真正的寒暄/跑题：只有短句命中这些词才算闲聊，避免「降价怎么办」被误判
CHITCHAT_HINTS = (
    "你好", "您好", "在吗", "在么", "谢谢", "再见", "天气", "你是谁",
    "你叫什么", "早上好", "晚上好", "哈哈",
)


def classify(text: str) -> str:
    normalized = (text or "").strip().lower()
    if not normalized:
        return "chitchat"

    # 时效类先于 order：问"多久发货"要查规则，不是查自己的订单
    if any(hint in normalized for hint in DELIVERY_TIME_HINTS):
        return "policy"

    for intent, keywords in INTENTS:
        if any(kw in normalized for kw in keywords):
            return intent

    if len(normalized) <= 12 and any(hint in normalized for hint in CHITCHAT_HINTS):
        return "chitchat"

    # 认不出来但像个正经问题 → 先检索再回答，宁可说「没查到」也不要凭记忆编
    return "kb"


def router_node(state: ChatState) -> dict:
    intent = classify(last_user_text(state))
    log.info("router: intent=%s", intent)
    return {"intent": intent}
