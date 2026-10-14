package com.eshop.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.PageResult;
import com.eshop.common.result.ResultCode;
import com.eshop.product.cache.ProductCacheService;
import com.eshop.product.dto.AdminCategoryRequest;
import com.eshop.product.dto.SpuSaveRequest;
import com.eshop.product.entity.Category;
import com.eshop.product.entity.Sku;
import com.eshop.product.entity.Spu;
import com.eshop.product.mapper.CategoryMapper;
import com.eshop.product.mapper.SkuMapper;
import com.eshop.product.mapper.SpuMapper;
import com.eshop.product.vo.AdminSpuVO;
import com.eshop.product.vo.SpuDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端商品服务（P3）：SPU 全量分页 / 上下架 / 新增编辑 + 分类维护
 * <p>
 * 与 C 端的联动：商品变更后走 P2 缓存失效通道（删除 + 延迟双删）；上架商品补入布隆过滤器，
 * 保证新商品立刻可被缓存层正确识别。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final ProductCacheService productCacheService;
    private final ProductService productService;

    /**
     * 商品详情（含 SKU 明细，不限状态；供管理端 SKU 展开 / 编辑回填）
     */
    public SpuDetailVO spuDetail(Long spuId) {
        SpuDetailVO vo = productService.loadSpuDetailForAdmin(spuId);
        if (vo == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return vo;
    }

    // ==================== SPU ====================

    /**
     * 商品分页（含下架；keyword / categoryId / status 可选筛选）
     */
    public PageResult<AdminSpuVO> spuPage(long page, long size, String keyword,
                                          Long categoryId, Integer status) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        IPage<AdminSpuVO> result = spuMapper.selectAdminSpuPage(
                new Page<>(safePage, safeSize), categoryId, kw, status);
        List<AdminSpuVO> list = result.getRecords();
        fillCategoryNames(list);
        fillSkuCounts(list);
        return PageResult.of(result.getTotal(), safePage, safeSize, list);
    }

    /**
     * 上下架：更新状态 + 缓存失效（删除 + 延迟双删）；上架商品补入布隆过滤器
     */
    public void updateStatus(Long spuId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "status 仅支持 0（下架）或 1（上架）");
        }
        requireSpu(spuId);
        spuMapper.update(null, new LambdaUpdateWrapper<Spu>()
                .set(Spu::getStatus, status)
                .eq(Spu::getId, spuId));
        productCacheService.evictSpuDetail(spuId, true);
        if (status == 1) {
            productCacheService.addSpuToBloom(spuId);
        }
        log.info("管理端{}商品: spuId={}", status == 1 ? "上架" : "下架", spuId);
    }

    /**
     * 新增商品（含初始 SKU 列表；默认下架状态，确认无误后由管理员上架）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSpu(SpuSaveRequest request) {
        requireCategory(request.getCategoryId());
        Spu spu = new Spu();
        spu.setName(request.getName().trim());
        spu.setCategoryId(request.getCategoryId());
        spu.setBrandId(request.getBrandId() == null ? 0L : request.getBrandId());
        spu.setMainImage(request.getMainImage() == null ? "" : request.getMainImage());
        spu.setImages(List.of());
        spu.setDetail(request.getDetail() == null ? "" : request.getDetail());
        spu.setStatus(0);
        spu.setSales(0);
        spuMapper.insert(spu);

        if (request.getSkus() != null) {
            for (SpuSaveRequest.SkuItem item : request.getSkus()) {
                Sku sku = new Sku();
                sku.setSpuId(spu.getId());
                sku.setSkuCode("SKU" + spu.getId() + "-" + (System.currentTimeMillis() % 100000));
                sku.setName(item.getName().trim());
                sku.setPrice(item.getPrice());
                sku.setMarketPrice(item.getMarketPrice() == null ? item.getPrice() : item.getMarketPrice());
                sku.setImage(item.getImage() == null ? "" : item.getImage());
                sku.setStatus(1);
                skuMapper.insert(sku);
            }
        }
        log.info("管理端新增商品: spuId={}, name={}, skus={}", spu.getId(), spu.getName(),
                request.getSkus() == null ? 0 : request.getSkus().size());
        return spu.getId();
    }

    /**
     * 编辑商品基本信息（SKU 编辑留待后续迭代）
     */
    public void updateSpu(Long spuId, SpuSaveRequest request) {
        requireSpu(spuId);
        requireCategory(request.getCategoryId());
        spuMapper.update(null, new LambdaUpdateWrapper<Spu>()
                .set(Spu::getName, request.getName().trim())
                .set(Spu::getCategoryId, request.getCategoryId())
                .set(Spu::getBrandId, request.getBrandId() == null ? 0L : request.getBrandId())
                .set(Spu::getMainImage, request.getMainImage() == null ? "" : request.getMainImage())
                .set(Spu::getDetail, request.getDetail() == null ? "" : request.getDetail())
                .eq(Spu::getId, spuId));
        productCacheService.evictSpuDetail(spuId, true);
        log.info("管理端编辑商品: spuId={}", spuId);
    }

    // ==================== 分类 ====================

    /** 全量分类（含停用，管理端视角） */
    public List<Category> categoryList() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    public Long createCategory(AdminCategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName().trim());
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setLevel(request.getLevel() != null ? request.getLevel()
                : (category.getParentId() == 0 ? 1 : 2));
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        category.setIcon(request.getIcon() == null ? "" : request.getIcon());
        categoryMapper.insert(category);
        productCacheService.evictCategories(true);
        log.info("管理端新增分类: id={}, name={}", category.getId(), category.getName());
        return category.getId();
    }

    public void updateCategory(Long id, AdminCategoryRequest request) {
        requireCategory(id);
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                .set(Category::getName, request.getName().trim())
                .set(Category::getParentId, parentId)
                .set(Category::getLevel, request.getLevel() != null ? request.getLevel()
                        : (parentId == 0 ? 1 : 2))
                .set(Category::getSort, request.getSort() == null ? 0 : request.getSort())
                .set(Category::getStatus, request.getStatus() == null ? 1 : request.getStatus())
                .set(Category::getIcon, request.getIcon() == null ? "" : request.getIcon())
                .eq(Category::getId, id));
        productCacheService.evictCategories(true);
        log.info("管理端编辑分类: id={}", id);
    }

    /** 删除分类：存在子分类或商品引用时拒绝（保护数据完整性） */
    public void deleteCategory(Long id) {
        requireCategory(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "存在子分类，无法删除");
        }
        Long spuCount = spuMapper.selectCount(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getCategoryId, id));
        if (spuCount != null && spuCount > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "该分类下存在商品，无法删除");
        }
        categoryMapper.deleteById(id);
        productCacheService.evictCategories(true);
        log.info("管理端删除分类: id={}", id);
    }

    // ==================== 内部工具 ====================

    /** 回填分类名（一次批量查询，避免 N+1） */
    private void fillCategoryNames(List<AdminSpuVO> list) {
        if (list.isEmpty()) {
            return;
        }
        Set<Long> ids = list.stream().map(AdminSpuVO::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = categoryMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        list.forEach(vo -> vo.setCategoryName(nameMap.getOrDefault(vo.getCategoryId(), "")));
    }

    /** 回填 SKU 数量（一次分组查询） */
    private void fillSkuCounts(List<AdminSpuVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> spuIds = list.stream().map(AdminSpuVO::getId).toList();
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .in(Sku::getSpuId, spuIds)
                .select(Sku::getSpuId));
        Map<Long, Long> countMap = skus.stream()
                .collect(Collectors.groupingBy(Sku::getSpuId, Collectors.counting()));
        list.forEach(vo -> vo.setSkuCount(countMap.getOrDefault(vo.getId(), 0L).intValue()));
    }

    private Spu requireSpu(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return spu;
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "分类不存在");
        }
        return category;
    }
}
