package com.lmeng.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lmeng.dto.LoginFormDTO;
import com.lmeng.dto.ResultUtils;
import com.lmeng.model.User;

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
     * 用户注册
     * @return
     */
    ResultUtils sign();

    /**
     * 用户签到
     * @return
     */
    ResultUtils signCount();

    /**
     * 注销功能
     * @param token
     * @return
     */
    ResultUtils logout(String token);
}
