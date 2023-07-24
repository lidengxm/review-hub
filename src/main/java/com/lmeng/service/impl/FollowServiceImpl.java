package com.lmeng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lmeng.dto.ResultUtils;
import com.lmeng.dto.UserDTO;
import com.lmeng.model.Follow;
import com.lmeng.mapper.FollowMapper;
import com.lmeng.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lmeng.service.IUserService;
import com.lmeng.global.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;


    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public ResultUtils follow(Long followUserId, Boolean isFollow) {
        //先获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        if(userId == null) {
            return ResultUtils.fail("用户未登录");
        }
        //1.判断是关注还是取关
        if (isFollow) {
            //2.关注用户，向数据库中添加数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess1 = save(follow);
            if(isSuccess1) {
                //如果保存数据库成功,就把关注用户的id存入redis的set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id",userId).eq("follow_user_id",followUserId);
            boolean isSuccess = remove(queryWrapper);
            if (isSuccess) {
                //把关注用户的id从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return ResultUtils.ok();
    }

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    @Override
    public ResultUtils isFollow(Long followUserId) {
        //1.先获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注 select * from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return ResultUtils.ok(count > 0);
    }

    /**
     * 查看当前用户和目标用户的共同关注
     * @param id
     * @return
     */
    @Override
    public ResultUtils followCommons(Long id) {
        //1.先获取当前用户
        Long userId = UserHolder.getUser().getId();
        if(userId == null) {
            return ResultUtils.fail("用户未登录");
        }
        String key = "follows:" + userId; //当前用户key
        //2.求当前用户和其他用户的共同关注 sinter key1 key2
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if(intersect == null || intersect.isEmpty()) {
            //没有交集就直接返回空集合
            return ResultUtils.ok(Collections.emptyList());
        }
        //3.解析用户id，将Redis中交集的id集合转为List集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户，将User对象转为UserDto对象
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return ResultUtils.ok(userDTOS);
    }
}
