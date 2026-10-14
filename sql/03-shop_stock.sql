-- =============================================================
-- shop_stock · 库存服务（2 表；流水表不做逻辑删除）
-- =============================================================
USE shop_stock;

-- 库存表（每 SKU 一行；乐观锁并发控制）
CREATE TABLE IF NOT EXISTS t_stock (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id      BIGINT   NOT NULL COMMENT 'SKU ID',
    available   INT      NOT NULL DEFAULT 0 COMMENT '可售库存',
    locked      INT      NOT NULL DEFAULT 0 COMMENT '锁定库存（已下单未支付确认）',
    sold        INT      NOT NULL DEFAULT 0 COMMENT '已售数量',
    version     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_id (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存表';

-- 库存流水表（锁定 / 确认扣减 / 回补 全量留痕，用于对账与排查）
CREATE TABLE IF NOT EXISTS t_stock_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id           BIGINT       NOT NULL COMMENT 'SKU ID',
    order_no         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '关联订单号',
    type             TINYINT      NOT NULL COMMENT '类型：1 锁定 / 2 确认扣减 / 3 回补',
    quantity         INT          NOT NULL COMMENT '变动数量',
    before_available INT          NOT NULL COMMENT '变动前可售库存',
    after_available  INT          NOT NULL COMMENT '变动后可售库存',
    remark           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sku_id (sku_id),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存流水表';
