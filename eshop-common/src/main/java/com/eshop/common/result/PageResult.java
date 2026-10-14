package com.eshop.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应结构（见 docs/api-contract.md §1.5）
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总条数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long page;

    /** 每页条数 */
    private long size;

    /** 当前页数据 */
    private List<T> list;

    public static <T> PageResult<T> of(long total, long page, long size, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setList(list);
        return result;
    }
}
