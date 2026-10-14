-- =============================================================
-- E-Shop 电商商城 · 数据库初始化（MySQL 8.0）
-- 说明：按服务逻辑分库（物理同实例）
-- 注意：MySQL 8 的 CREATE DATABASE 不支持 COMMENT，以下用 -- 注释标明用途
-- =============================================================

-- 用户服务：用户账号与收货地址
CREATE DATABASE IF NOT EXISTS shop_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 商品服务：分类、品牌、SPU、SKU
CREATE DATABASE IF NOT EXISTS shop_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 库存服务：库存与流水
CREATE DATABASE IF NOT EXISTS shop_stock DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 订单服务：订单主表、明细、状态日志
CREATE DATABASE IF NOT EXISTS shop_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 支付服务：支付单与回调日志
CREATE DATABASE IF NOT EXISTS shop_pay DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
