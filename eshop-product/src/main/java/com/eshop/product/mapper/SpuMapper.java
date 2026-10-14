package com.eshop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eshop.product.entity.Spu;
import com.eshop.product.vo.SpuListItemVO;
import org.apache.ibatis.annotations.Param;

/**
 * 商品 SPU Mapper（分页查询见 resources/mapper/SpuMapper.xml）
 */
public interface SpuMapper extends BaseMapper<Spu> {

    /**
     * 商品列表分页：price / marketPrice 取该 SPU 下最低价 SKU 的价格（见 XML 注释）
     *
     * @param sort default（上新优先）/ sales / priceAsc / priceDesc
     */
    IPage<SpuListItemVO> selectSpuPage(IPage<SpuListItemVO> page,
                                       @Param("categoryId") Long categoryId,
                                       @Param("keyword") String keyword,
                                       @Param("sort") String sort);
}
