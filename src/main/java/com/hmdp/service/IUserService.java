package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.ResultUtils;
import com.hmdp.model.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    ResultUtils sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    ResultUtils login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 注册
     * @return
     */
    ResultUtils sign();

    /**
     * 注销
     * @return
     */
    ResultUtils signCount();
}
