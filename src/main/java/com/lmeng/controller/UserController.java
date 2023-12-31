package com.lmeng.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lmeng.dto.LoginFormDTO;
import com.lmeng.dto.ResultUtils;
import com.lmeng.dto.UserDTO;
import com.lmeng.model.User;
import com.lmeng.model.UserInfo;
import com.lmeng.service.IUserInfoService;
import com.lmeng.service.IUserService;
import com.lmeng.global.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public ResultUtils sendCode(@RequestParam("phone") String phone, HttpSession session) {
        //发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public ResultUtils login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 注销功能
     * @return 无
     */
    @PostMapping("/logout")
    public ResultUtils logout(String token){
        //取消登录态，注销
        return userService.logout(token);
    }

    @GetMapping("/me")
    public ResultUtils me(){
        //获取当前登录的用户并返回
        UserDTO user =  UserHolder.getUser();
        return ResultUtils.ok(user);
    }

    @GetMapping("/info/{id}")
    public ResultUtils info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return ResultUtils.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return ResultUtils.ok(info);
    }

    @GetMapping("/{id}")
    public ResultUtils queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return ResultUtils.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return ResultUtils.ok(userDTO);
    }

    /**
     * 用户签到
     * @return
     */
    @PostMapping("/sign")
    public ResultUtils sign() {
        return userService.sign();
    }

    /**
     * 用户签到统计
     * @return
     */
    @GetMapping("/sign/count")
    public ResultUtils signCount() {
        return userService.signCount();
    }


}
