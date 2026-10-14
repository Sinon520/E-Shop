package com.eshop.order.config;

import feign.RequestInterceptor;
import org.apache.seata.core.context.RootContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seata XID 传播（Feign 客户端侧）
 * <p>
 * seata-spring-boot-starter 自带 HTTP 服务端集成（下游从请求头恢复 XID），
 * 但未内置 Feign 客户端集成——这里显式补齐：全局事务内发起远程调用时，
 * 将当前 XID 写入请求头（TX_XID），下游 RM（如 eshop-stock）据此加入同一全局事务、
 * 注册分支并具备 undo_log 回滚能力。
 */
@Configuration
public class SeataFeignConfig {

    @Bean
    public RequestInterceptor seataXidRequestInterceptor() {
        return template -> {
            String xid = RootContext.getXID();
            if (xid != null && !xid.isEmpty()) {
                template.header(RootContext.KEY_XID, xid);
            }
        };
    }
}
