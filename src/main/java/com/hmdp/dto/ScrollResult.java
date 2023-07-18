package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {//展示分页查询结果
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
