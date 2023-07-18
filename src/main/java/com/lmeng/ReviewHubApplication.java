package com.lmeng;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.lmeng.mapper")
@SpringBootApplication
public class ReviewHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewHubApplication.class, args);
    }

}
