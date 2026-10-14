-- =============================================================
-- shop_order · 订单服务（3 表）
-- 状态机：0 待支付 → 1 已支付 → 2 已发货 → 3 已完成；4 已取消 / 5 超时关闭
-- =============================================================
USE shop_order;

-- 订单主表
CREATE TABLE IF NOT EXISTS t_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号（业务唯一）',
    user_id          BIGINT        NOT NULL COMMENT '用户 ID',
    total_amount     DECIMAL(10,2) NOT NULL COMMENT '商品总额',
    pay_amount       DECIMAL(10,2) NOT NULL COMMENT '应付金额',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0 待支付 / 1 已支付 / 2 已发货 / 3 已完成 / 4 已取消 / 5 超时关闭',
    receiver_name    VARCHAR(32)   NOT NULL COMMENT '收货人姓名（快照）',
    receiver_phone   VARCHAR(20)   NOT NULL COMMENT '收货人手机号（快照）',
    receiver_address VARCHAR(255)  NOT NULL COMMENT '收货地址（省市区+详细，快照）',
    remark           VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '订单备注',
    pay_time         DATETIME      NULL COMMENT '支付时间',
    ship_time        DATETIME      NULL COMMENT '发货时间',
    finish_time      DATETIME      NULL COMMENT '完成时间',
    close_time       DATETIME      NULL COMMENT '关闭/取消时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id_status (user_id, status),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单主表';

-- 订单明细表（商品信息全部为下单时快照，不依赖商品服务）
CREATE TABLE IF NOT EXISTS t_order_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)   NOT NULL COMMENT '订单号',
    spu_id       BIGINT        NOT NULL COMMENT 'SPU ID',
    sku_id       BIGINT        NOT NULL COMMENT 'SKU ID',
    spu_name     VARCHAR(128)  NOT NULL COMMENT '商品名称（快照）',
    sku_name     VARCHAR(128)  NOT NULL COMMENT '规格名称（快照）',
    sku_image    VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '商品图（快照）',
    price        DECIMAL(10,2) NOT NULL COMMENT '成交单价（快照）',
    quantity     INT           NOT NULL COMMENT '购买数量',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '小计金额',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_no (order_no),
    KEY idx_sku_id (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细表';

-- 订单状态流转日志
CREATE TABLE IF NOT EXISTS t_order_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32)  NOT NULL COMMENT '订单号',
    from_status   TINYINT      NOT NULL COMMENT '原状态',
    to_status     TINYINT      NOT NULL COMMENT '新状态',
    operator_type TINYINT      NOT NULL DEFAULT 3 COMMENT '操作者类型：1 用户 / 2 管理员 / 3 系统',
    operator_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '操作者 ID',
    remark        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单状态流转日志表';
