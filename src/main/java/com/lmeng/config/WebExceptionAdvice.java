package com.lmeng.config;

import com.lmeng.dto.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public ResultUtils handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return ResultUtils.fail("服务器异常");
    }
}
