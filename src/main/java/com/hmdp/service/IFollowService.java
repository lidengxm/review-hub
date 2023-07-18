package com.hmdp.service;

import com.hmdp.dto.ResultUtils;
import com.hmdp.model.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    ResultUtils follow(Long followUserId, Boolean isFollow);

    ResultUtils isFollow(Long followUserId);

    ResultUtils followCommons(Long id);
}
