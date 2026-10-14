package com.eshop.stock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 库存 Redis 脚本配置（Lua 原子操作）
 */
@Configuration
public class StockRedisConfig {

    /** 预扣脚本：原子「判断余量 + 扣减」 */
    @Bean
    public DefaultRedisScript<Long> stockDeductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** 加回脚本：仅当键存在时加回（回滚预扣 / 取消回补） */
    @Bean
    public DefaultRedisScript<Long> stockRestoreScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/stock_restore.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
