package com.hmdp.config;

import com.hmdp.global.LoginInterceptor;
import com.hmdp.global.RefreshTikenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @version 1.0
 * @learner Lmeng
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器并配置拦截器不需要拦截的路径
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/short-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);
        //刷新token的拦截器，需要先执行
        registry.addInterceptor(new RefreshTikenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }
}
