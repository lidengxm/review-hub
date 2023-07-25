package com.lmeng.dto;

import lombok.Data;

import java.util.List;

/**
 * //展示分页查询结果
 */
@Data
public class ScrollResult {

    private List<?> list;

    private Long minTime;

    private Integer offset;
}
