package com.eshop.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.result.PageResult;
import com.eshop.product.entity.Brand;
import com.eshop.product.entity.Category;
import com.eshop.product.entity.Sku;
import com.eshop.product.entity.Spu;
import com.eshop.product.mapper.BrandMapper;
import com.eshop.product.mapper.CategoryMapper;
import com.eshop.product.mapper.SkuMapper;
import com.eshop.product.mapper.SpuMapper;
import com.eshop.product.vo.CategoryVO;
import com.eshop.product.vo.SkuVO;
import com.eshop.product.vo.SpecGroupVO;
import com.eshop.product.vo.SpuDetailVO;
import com.eshop.product.vo.SpuListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品查询服务：分类 / 商品列表 / 商品详情 / SKU 批量（内部）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final long MAX_PAGE_SIZE = 100;

    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;

    /**
     * 分类列表（启用中，sort 升序；平铺返回，前端按 parentId 组装）
     */
    public List<CategoryVO> listCategories() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        return categories.stream().map(category -> {
            CategoryVO vo = new CategoryVO();
            vo.setId(category.getId());
            vo.setParentId(category.getParentId());
            vo.setName(category.getName());
            vo.setLevel(category.getLevel());
            vo.setIcon(category.getIcon());
            vo.setSort(category.getSort());
            return vo;
        }).toList();
    }

    /**
     * 商品列表分页（筛选 / 搜索 / 排序，见 docs/api-contract.md §3.2）
     */
    public PageResult<SpuListItemVO> pageSpu(long page, long size, Long categoryId, String keyword, String sort) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeSort = StringUtils.hasText(sort) ? sort : "default";
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        IPage<SpuListItemVO> result = spuMapper.selectSpuPage(
                new Page<>(safePage, safeSize), categoryId, safeKeyword, safeSort);
        List<SpuListItemVO> list = result.getRecords();
        fillCategoryNames(list);
        return PageResult.of(result.getTotal(), safePage, safeSize, list);
    }

    /**
     * 上架 SPU 的 id 列表（缓存预热：布隆过滤器重建 + 详情缓存兜底）
     */
    public List<Long> listOnlineSpuIds() {
        return spuMapper.selectList(new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getStatus, 1)
                        .select(Spu::getId))
                .stream()
                .map(Spu::getId)
                .toList();
    }

    /**
     * 从 DB 加载商品详情：含分类/品牌名、specs 聚合、SKU 列表
     * <p>
     * 供缓存层回源调用：返回 null 表示商品不存在或已下架，
     * 由 {@link com.eshop.product.cache.ProductCacheService} 统一转换为 2001 错误码。
     */
    public SpuDetailVO loadSpuDetail(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null || spu.getStatus() == null || spu.getStatus() != 1) {
            return null;
        }

        SpuDetailVO vo = new SpuDetailVO();
        vo.setId(spu.getId());
        vo.setName(spu.getName());
        vo.setCategoryId(spu.getCategoryId());
        vo.setMainImage(spu.getMainImage());
        vo.setImages(spu.getImages() == null ? List.of() : spu.getImages());
        vo.setDetail(spu.getDetail());
        vo.setStatus(spu.getStatus());
        vo.setSales(spu.getSales());

        Category category = spu.getCategoryId() == null ? null : categoryMapper.selectById(spu.getCategoryId());
        vo.setCategoryName(category == null ? "" : category.getName());
        if (spu.getBrandId() != null && spu.getBrandId() > 0) {
            Brand brand = brandMapper.selectById(spu.getBrandId());
            vo.setBrandName(brand == null ? "" : brand.getName());
        } else {
            vo.setBrandName("");
        }

        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getSpuId, spuId)
                .orderByAsc(Sku::getId));
        vo.setSkus(skus.stream().map(this::toSkuVO).toList());
        vo.setSpecs(aggregateSpecs(skus));
        return vo;
    }

    /**
     * 内部接口：批量查询 SKU 信息（eshop-cart 购物车展示 / eshop-order 下单快照 / 管理端库存展示）
     * <p>
     * 默认仅返回在架 SKU；管理端场景 includeOffline=true 时含下架 SKU；
     * 调用方按返回结果过滤（查不到 = 已删除）
     */
    public List<SkuInfoDTO> listSkuInfo(List<Long> skuIds, boolean includeOffline) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .in(Sku::getId, skuIds)
                .eq(!includeOffline, Sku::getStatus, 1));
        if (skus.isEmpty()) {
            return List.of();
        }

        Set<Long> spuIds = skus.stream().map(Sku::getSpuId).collect(Collectors.toSet());
        Map<Long, Spu> spuMap = spuMapper.selectByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, spu -> spu));

        return skus.stream().map(sku -> {
            Spu spu = spuMap.get(sku.getSpuId());
            SkuInfoDTO dto = new SkuInfoDTO();
            dto.setSkuId(sku.getId());
            dto.setSpuId(sku.getSpuId());
            dto.setSpuName(spu == null ? "" : spu.getName());
            dto.setSkuName(sku.getName());
            dto.setImage(StringUtils.hasText(sku.getImage()) ? sku.getImage()
                    : (spu == null ? "" : spu.getMainImage()));
            dto.setPrice(sku.getPrice());
            return dto;
        }).toList();
    }

    /** 列表元素回填分类名（一次批量查询，避免 N+1） */
    private void fillCategoryNames(List<SpuListItemVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = list.stream()
                .map(SpuListItemVO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        list.forEach(vo -> vo.setCategoryName(nameMap.getOrDefault(vo.getCategoryId(), "")));
    }

    private SkuVO toSkuVO(Sku sku) {
        SkuVO vo = new SkuVO();
        vo.setId(sku.getId());
        vo.setSkuCode(sku.getSkuCode());
        vo.setName(sku.getName());
        vo.setSpecJson(sku.getSpecJson());
        vo.setPrice(sku.getPrice());
        vo.setMarketPrice(sku.getMarketPrice());
        vo.setImage(sku.getImage());
        vo.setStatus(sku.getStatus());
        return vo;
    }

    /**
     * 规格聚合：遍历 SKU 的 specJson，按首次出现的顺序收集规格名与去重后的值
     */
    private List<SpecGroupVO> aggregateSpecs(List<Sku> skus) {
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (Sku sku : skus) {
            Map<String, String> spec = sku.getSpecJson();
            if (spec == null || spec.isEmpty()) {
                continue;
            }
            spec.forEach((name, value) ->
                    grouped.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(value));
        }
        return grouped.entrySet().stream().map(entry -> {
            SpecGroupVO group = new SpecGroupVO();
            group.setName(entry.getKey());
            group.setValues(new ArrayList<>(entry.getValue()));
            return group;
        }).toList();
    }
}
