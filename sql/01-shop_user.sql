-- =============================================================
-- shop_user · 用户服务（2 表）
-- =============================================================
USE shop_user;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名（登录名）',
    password    VARCHAR(64)  NOT NULL COMMENT '密码（BCrypt 哈希）',
    nickname    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    phone       VARCHAR(20)  NULL DEFAULT NULL COMMENT '手机号（可空；唯一索引多 NULL 不冲突）',
    email       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '邮箱',
    avatar      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像 URL',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username, deleted),
    UNIQUE KEY uk_phone (phone, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- 收货地址表
CREATE TABLE IF NOT EXISTS t_user_address (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '用户 ID',
    receiver_name  VARCHAR(32)  NOT NULL COMMENT '收货人姓名',
    receiver_phone VARCHAR(20)  NOT NULL COMMENT '收货人手机号',
    province       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '省',
    city           VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '市',
    district       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '区/县',
    detail_address VARCHAR(255) NOT NULL DEFAULT '' COMMENT '详细地址',
    is_default     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认：1 是 / 0 否',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收货地址表';
