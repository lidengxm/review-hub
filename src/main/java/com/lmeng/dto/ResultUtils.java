package com.lmeng.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultUtils {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static ResultUtils ok(){
        return new ResultUtils(true, null, null, null);
    }
    public static ResultUtils ok(Object data){
        return new ResultUtils(true, null, data, null);
    }
    public static ResultUtils ok(List<?> data, Long total){
        return new ResultUtils(true, null, data, total);
    }
    public static ResultUtils fail(String errorMsg){
        return new ResultUtils(false, errorMsg, null, null);
    }
}
