-- =============================================================
-- E-Shop 管理后台：管理员账号表 + 种子数据（P3）
-- 作用于 shop_user 库；与普通用户体系（t_user）完全独立
-- 可重复执行（IF NOT EXISTS + 固定主键）
-- =============================================================

USE shop_user;

CREATE TABLE IF NOT EXISTS `t_admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(32)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 哈希）',
    `name`        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '显示名',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='管理员账号（管理后台独立体系）';

-- 种子：admin / admin123（BCrypt 哈希由 spring-security-crypto 生成，与用户密码同算法）
INSERT IGNORE INTO `t_admin` (`id`, `username`, `password`, `name`, `status`) VALUES
    (1, 'admin', '$2a$10$KqsIi/CK64zpiWDpiNN9u.kIAlOGuVlm.V6mwwTqA9.b23GX.1kHK', '超级管理员', 1);
