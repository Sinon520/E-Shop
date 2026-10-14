-- =============================================================
-- shop_pay · 支付服务（2 表；回调日志表不做逻辑删除）
-- =============================================================
USE shop_pay;

-- 支付单表（一笔有效支付对应一笔支付单）
CREATE TABLE IF NOT EXISTS t_payment (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_no  VARCHAR(32)   NOT NULL COMMENT '支付单号（业务唯一）',
    order_no    VARCHAR(32)   NOT NULL COMMENT '关联订单号',
    user_id     BIGINT        NOT NULL COMMENT '用户 ID',
    amount      DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    pay_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '支付方式：1 模拟支付',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0 待支付 / 1 支付成功 / 2 支付失败',
    pay_time    DATETIME      NULL COMMENT '支付成功时间',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付单表';

-- 支付回调日志表（幂等核心：uk_out_trade_no 挡住重复回调）
CREATE TABLE IF NOT EXISTS t_pay_callback_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_no     VARCHAR(32)  NOT NULL COMMENT '支付单号',
    out_trade_no   VARCHAR(64)  NOT NULL COMMENT '第三方交易流水号（唯一，幂等去重键）',
    raw_content    TEXT         NULL COMMENT '原始回调报文',
    process_status TINYINT      NOT NULL DEFAULT 0 COMMENT '处理状态：0 未处理 / 1 已处理',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_payment_no (payment_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付回调日志表';
