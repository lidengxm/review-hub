package com.lmeng.dto;

import lombok.Data;

/**
 * 后端登录接收参数
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
