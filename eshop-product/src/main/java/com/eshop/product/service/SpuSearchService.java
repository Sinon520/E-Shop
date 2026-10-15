package com.eshop.product.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.common.result.PageResult;
import com.eshop.product.config.EsProperties;
import com.eshop.product.entity.Category;
import com.eshop.product.entity.Sku;
import com.eshop.product.entity.Spu;
import com.eshop.product.mapper.CategoryMapper;
import com.eshop.product.mapper.SkuMapper;
import com.eshop.product.mapper.SpuMapper;
import com.eshop.product.vo.SkuVO;
import com.eshop.product.vo.SpuDetailVO;
import com.eshop.product.vo.SpuListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SPU 搜索服务（P4：Elasticsearch + IK 分词）
 * <p>
 * 设计要点：
 * <ul>
 *   <li>索引内保留 status 字段，查询时过滤上架商品——上架/下架/编辑统一走 {@link #syncSpu}；
 *       商品逻辑删除（查不到记录）时同步删除 ES 文档；</li>
 *   <li>所有写操作失败仅告警不阻断主流程（ES 是搜索加速层，非数据依赖层）；</li>
 *   <li>搜索失败由调用方降级到 DB like 查询；</li>
 *   <li>启动时异步全量重建（数据量小，保证 ES 与 DB 一致，同时兼做首次建索引）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eshop.es", name = "enabled", havingValue = "true")
public class SpuSearchService {

    private final EsProperties props;
    private final ElasticsearchClient client;
    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final ProductService productService;

    /** SPU 搜索文档（eshop_spu） */
    public record SpuDoc(Long id, String name, Long categoryId, String categoryName,
                         String brandName, String mainImage, BigDecimal price,
                         BigDecimal marketPrice, Integer sales, Integer status, String detail) {
    }

    // ==================== 索引管理 ====================

    /** 索引不存在则创建（单分片零副本 + IK 分词字段） */
    public void ensureIndex() throws Exception {
        boolean exists = client.indices().exists(e -> e.index(props.getIndex())).value();
        if (exists) {
            return;
        }
        client.indices().create(c -> c
                .index(props.getIndex())
                .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                .mappings(m -> m
                        .properties("id", p -> p.long_(l -> l))
                        .properties("name", p -> p.text(t -> t
                                .analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                .fields("keyword", f -> f.keyword(k -> k))))
                        .properties("categoryId", p -> p.long_(l -> l))
                        .properties("categoryName", p -> p.text(t -> t
                                .analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                .fields("keyword", f -> f.keyword(k -> k))))
                        .properties("brandName", p -> p.text(t -> t
                                .analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("mainImage", p -> p.keyword(k -> k.index(false)))
                        .properties("price", p -> p.double_(d -> d))
                        .properties("marketPrice", p -> p.double_(d -> d))
                        .properties("sales", p -> p.integer(i -> i))
                        .properties("status", p -> p.integer(i -> i))
                        .properties("detail", p -> p.text(t -> t
                                .analyzer("ik_max_word").searchAnalyzer("ik_smart")))));
        log.info("ES 索引已创建: {}", props.getIndex());
    }

    // ==================== 同步 ====================

    /**
     * 商品变更同步（新增 / 编辑 / 上下架后调用）；
     * 商品已删除（逻辑删除查不到）时删除对应文档。
     */
    public void syncSpu(Long spuId) {
        try {
            Spu spu = spuMapper.selectById(spuId);
            if (spu == null) {
                deleteDoc(spuId);
                return;
            }
            SpuDetailVO detail = productService.buildSpuDetail(spu);
            List<SkuVO> skus = detail.getSkus() == null ? List.of() : detail.getSkus();
            BigDecimal minPrice = skus.stream()
                    .map(SkuVO::getPrice).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            BigDecimal marketPrice = skus.stream()
                    .filter(sku -> Objects.equals(sku.getPrice(), minPrice))
                    .map(SkuVO::getMarketPrice).filter(Objects::nonNull)
                    .findFirst().orElse(null);

            SpuDoc doc = new SpuDoc(spu.getId(), spu.getName(), spu.getCategoryId(),
                    detail.getCategoryName(), detail.getBrandName(), spu.getMainImage(),
                    minPrice, marketPrice, spu.getSales(), spu.getStatus(), plainText(spu.getDetail()));
            client.index(i -> i.index(props.getIndex()).id(String.valueOf(spuId)).document(doc));
            log.debug("ES 同步 SPU: id={} status={}", spuId, spu.getStatus());
        } catch (Exception e) {
            log.warn("ES 同步 SPU 失败（不影响主流程）: id={} err={}", spuId, e.getMessage());
        }
    }

    /** 删除文档（文档不存在视为成功） */
    public void deleteDoc(Long spuId) {
        try {
            client.delete(d -> d.index(props.getIndex())
                    .id(String.valueOf(spuId)).refresh(Refresh.True));
        } catch (Exception e) {
            log.warn("ES 删除文档失败: id={} err={}", spuId, e.getMessage());
        }
    }

    /**
     * 全量重建：清空索引后按 DB 批量写入（管理端运维接口 / 启动时自动执行）
     *
     * @return 同步的文档数
     */
    public long reindexAll() throws Exception {
        ensureIndex();
        client.deleteByQuery(d -> d.index(props.getIndex())
                .query(q -> q.matchAll(m -> m)).refresh(true));

        List<Spu> spus = spuMapper.selectList(new LambdaQueryWrapper<Spu>());
        Map<Long, BigDecimal> minPriceMap = loadMinPriceMap();
        if (!spus.isEmpty()) {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (Spu spu : spus) {
                SpuDoc doc = toDoc(spu, minPriceMap.get(spu.getId()));
                bulk.operations(op -> op.index(idx -> idx
                        .index(props.getIndex())
                        .id(String.valueOf(spu.getId()))
                        .document(doc)));
            }
            var response = client.bulk(bulk.build());
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.warn("ES bulk 写入失败: id={} err={}", item.id(), item.error().reason());
                    }
                }
            }
            // 批量写后强制 refresh：保证重建结束即可检索（数据量小，代价可忽略）
            client.indices().refresh(r -> r.index(props.getIndex()));
        }
        log.info("ES 全量重建完成: {} 篇文档", spus.size());
        return spus.size();
    }

    // ==================== 搜索 ====================

    /**
     * 关键词搜索（仅上架商品）：multi_match name^3 / categoryName^2 / brandName / detail
     *
     * @param sort default 相关度 / sales 销量 / priceAsc / priceDesc
     */
    public PageResult<SpuListItemVO> search(String keyword, long page, long size,
                                            Long categoryId, String sort) throws Exception {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        int from = (int) ((safePage - 1) * safeSize);
        String kw = keyword == null ? "" : keyword.trim();

        SearchResponse<SpuDoc> response = client.search(s -> {
            s.index(props.getIndex()).from(from).size((int) safeSize);
            s.query(q -> q.bool(b -> {
                b.filter(f -> f.term(t -> t.field("status").value(1)));
                if (categoryId != null) {
                    b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
                }
                if (StringUtils.hasText(kw)) {
                    b.must(m -> m.multiMatch(mm -> mm
                            .query(kw)
                            .fields("name^3", "categoryName^2", "brandName", "detail")
                            .analyzer("ik_smart")));
                } else {
                    b.must(m -> m.matchAll(ma -> ma));
                }
                return b;
            }));
            switch (sort == null ? "default" : sort) {
                case "sales" -> s.sort(so -> so.field(f -> f.field("sales").order(SortOrder.Desc)));
                case "priceAsc" -> s.sort(so -> so.field(f -> f.field("price").order(SortOrder.Asc)));
                case "priceDesc" -> s.sort(so -> so.field(f -> f.field("price").order(SortOrder.Desc)));
                default -> { /* 相关度（默认 _score） */ }
            }
            return s;
        }, SpuDoc.class);

        long total = response.hits().total() == null ? 0 : response.hits().total().value();
        List<SpuListItemVO> list = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(this::toVO)
                .toList();
        return PageResult.of(total, safePage, safeSize, list);
    }

    // ==================== 内部工具 ====================

    private SpuDoc toDoc(Spu spu, BigDecimal minPrice) {
        Category category = spu.getCategoryId() == null ? null : categoryMapper.selectById(spu.getCategoryId());
        return new SpuDoc(spu.getId(), spu.getName(), spu.getCategoryId(),
                category == null ? "" : category.getName(), "", spu.getMainImage(),
                minPrice, null, spu.getSales(), spu.getStatus(), plainText(spu.getDetail()));
    }

    /** 全量重建用的最低价映射（一次查询，避免逐 SPU 查 SKU） */
    private Map<Long, BigDecimal> loadMinPriceMap() {
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .select(Sku::getSpuId, Sku::getPrice));
        return skus.stream()
                .filter(sku -> sku.getPrice() != null)
                .collect(Collectors.toMap(Sku::getSpuId, Sku::getPrice, BigDecimal::min));
    }

    private SpuListItemVO toVO(SpuDoc doc) {
        SpuListItemVO vo = new SpuListItemVO();
        vo.setId(doc.id());
        vo.setName(doc.name());
        vo.setMainImage(doc.mainImage());
        vo.setPrice(doc.price());
        vo.setMarketPrice(doc.marketPrice());
        vo.setSales(doc.sales());
        vo.setCategoryId(doc.categoryId());
        vo.setCategoryName(doc.categoryName());
        return vo;
    }

    /** 富文本详情去标签（详情正文参与检索，截断防止超大文档） */
    private String plainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", " ");
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    /** 启动后异步全量重建（首次建索引 / 漂移收敛；失败不影响启动） */
    @EventListener(ApplicationReadyEvent.class)
    public void reindexOnStartup() {
        if (!props.isStartupReindex()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                reindexAll();
            } catch (Exception e) {
                log.warn("ES 启动重建失败（搜索接口将降级 DB）: {}", e.getMessage());
            }
        }, "es-startup-reindex");
        thread.setDaemon(true);
        thread.start();
    }
}
