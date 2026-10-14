-- =============================================================
-- shop_product · 商品服务（4 表）
-- =============================================================
USE shop_product;

-- 商品分类表（两级）
CREATE TABLE IF NOT EXISTS t_category (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父级 ID，0 为一级分类',
    name        VARCHAR(32)  NOT NULL COMMENT '分类名称',
    level       TINYINT      NOT NULL DEFAULT 1 COMMENT '层级：1 一级 / 2 二级',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序值（越小越靠前）',
    icon        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '图标 URL',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品分类表';

-- 品牌表
CREATE TABLE IF NOT EXISTS t_brand (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)  NOT NULL COMMENT '品牌名称',
    logo        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '品牌 Logo URL',
    description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '品牌简介',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '品牌表';

-- 商品 SPU 表
CREATE TABLE IF NOT EXISTS t_spu (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '商品名称',
    category_id BIGINT       NOT NULL COMMENT '分类 ID',
    brand_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '品牌 ID（0 无品牌）',
    main_image  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '主图 URL',
    images      JSON         NULL COMMENT '轮播图 URL 数组',
    detail      MEDIUMTEXT   NULL COMMENT '商品详情（富文本 HTML）',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 下架 / 1 上架',
    sales       INT          NOT NULL DEFAULT 0 COMMENT '累计销量（冗余统计）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SPU 表';

-- 商品 SKU 表
CREATE TABLE IF NOT EXISTS t_sku (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id      BIGINT        NOT NULL COMMENT '所属 SPU ID',
    sku_code    VARCHAR(32)   NOT NULL COMMENT 'SKU 编码（仓储/对账用）',
    name        VARCHAR(128)  NOT NULL COMMENT '规格名称，如：白色 256G',
    spec_json   JSON          NULL COMMENT '规格键值对，如 {"颜色":"白色","内存":"256G"}',
    price       DECIMAL(10,2) NOT NULL COMMENT '销售价',
    market_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '划线价（市场价），列表/详情展示用',
    image       VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '规格图 URL',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0 下架 / 1 上架',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code, deleted),
    KEY idx_spu_id (spu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SKU 表';
