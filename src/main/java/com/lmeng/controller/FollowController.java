package com.lmeng.controller;

import com.lmeng.dto.ResultUtils;
import com.lmeng.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public ResultUtils follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public ResultUtils follow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public ResultUtils followCommons(@PathVariable("id") Long id) {
        return followService.followCommons(id);
    }
}
