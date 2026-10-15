package com.eshop.product.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置（P4 商品搜索）
 * <p>
 * eshop.es.enabled=false 时不创建任何 ES Bean（搜索服务经 {@link com.eshop.product.service.SpuSearchService}
 * 的可用性判断整体降级到 DB 查询）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(EsProperties.class)
@ConditionalOnProperty(prefix = "eshop.es", name = "enabled", havingValue = "true")
public class ElasticsearchConfig {

    private final EsProperties esProperties;

    @Bean(destroyMethod = "close")
    public RestClient esRestClient() {
        String host = esProperties.getHost();
        String hostname = host;
        int port = 9200;
        int colon = host.lastIndexOf(':');
        if (colon > 0) {
            hostname = host.substring(0, colon);
            port = Integer.parseInt(host.substring(colon + 1));
        }
        log.info("ES 搜索客户端初始化: {}:{} scheme={} index={}", hostname, port,
                esProperties.getScheme(), esProperties.getIndex());
        return RestClient.builder(new HttpHost(hostname, port, esProperties.getScheme()))
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(3000)
                        .setSocketTimeout(10000))
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient esRestClient) {
        return new ElasticsearchClient(new RestClientTransport(esRestClient, new JacksonJsonpMapper()));
    }
}
