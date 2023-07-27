package com.lmeng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lmeng.dto.ResultUtils;
import com.lmeng.dto.ScrollResult;
import com.lmeng.dto.UserDTO;
import com.lmeng.model.Blog;
import com.lmeng.model.Follow;
import com.lmeng.model.User;
import com.lmeng.mapper.BlogMapper;
import com.lmeng.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lmeng.service.IFollowService;
import com.lmeng.service.IUserService;
import com.lmeng.constant.SystemConstants;
import com.lmeng.global.UserHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lmeng.constant.RedisConstants.*;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * 分页查询热点博客
     * @param current
     * @return
     */
    @Override
    public ResultUtils queryHotBlog(Integer current) {
        List<Blog> records;
        //1.先查看缓存中是否有热点数据
        String key = CACHE_HOT_BLOG_KEY;
        String blogCacheJSON = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(blogCacheJSON)) {
            records = JSONUtil.toList(blogCacheJSON, Blog.class);
        } else {
            // 缓存中没有则根据用户查询数据库
            Page<Blog> page = blogService.query()
                    .orderByDesc("liked")
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
            // 获取当前页数据
            records = page.getRecords();
        }

        // 查询用户；查询是否被点赞，查询博客中的事务
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return ResultUtils.ok(records);
    }

    @Override
    public ResultUtils queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if(blog == null) {
            return ResultUtils.fail("该探店笔记不存在");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询Blog是否被点赞
        isBlogLiked(blog);
        return ResultUtils.ok(blog);
    }

    /**
     * 根据博客查询用户是否点赞
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        //1.获取登录用户，登录再查询用户是否点赞
        Long userId = UserHolder.getUser().getId();
        if (userId != null) {
            //2.判断当前用户是否点赞
            String key = BLOG_LIKED_KEY + blog.getId();
            Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
            blog.setIsLike(score != null);
        }
        //用户未登录无需查询是否点赞
    }

    /**
     * 点赞功能
     * @param id
     * @return
     */
    @Override
    public ResultUtils likeBlog(Long id) {
        //先获取当前用户
        Long userId = UserHolder.getUser().getId();
        if(userId == null) {
            return ResultUtils.fail("用户未登录");
        }
        //1.判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //2.如果未点赞，可以点赞
        if(score == null) {
            //2.1点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //2.2保存用户到redis的set集合，方便下次判断是否点赞
            if (isSuccess) {
                //更新数据库成功之后保存数据到zset集合 zadd key value score
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //3.如果已点赞，取消点赞；数据库点赞数-1，把用户id从redis的集合中移除
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                //更新数据库成功之后,把用户id从redis集合中移除
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return ResultUtils.ok();
    }

    /**
     * 查询点赞排行榜top5
     * @param id
     * @return
     */
    @Override
    public ResultUtils queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //判断top5的用户，如果没人点赞则返回空集合，避免空指针
        if (top5 == null || top5.isEmpty()) {
            return ResultUtils.ok(Collections.emptyList());
        }
        //2.解析出查到的（从redis中获得的是用户集合） 其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //3.根据用户id查询用户 where id in (5, 1) order by field(id, 5, 1)
        //将解析的User转为UserDto返回
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + " )").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return ResultUtils.ok(userDTOS);
    }

    /**
     * 新增笔记，新增笔记后推送到粉丝收件箱（保存在sortedset）
     * @param blog
     * @return
     */
    @Override
    public ResultUtils saveBlog(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2.保存探店笔记
        boolean isSuccess = save(blog);
        if(!isSuccess) {
            return ResultUtils.fail("新增笔记失败");
        }
        //3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> followsUserIdList = followService.query().eq("follow_user_id", user.getId()).list();
        //4.推送笔记给作者所有粉丝
        for (Follow follow : followsUserIdList) {
            //4.1获取每个粉丝id
            Long userId = follow.getUserId();
            //4.2推送，即将笔记保存到用户sortedset缓存集合中，分数是时间戳
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        //5.返回id
        return ResultUtils.ok(blog.getId());
    }

    /**
     * 查询收件箱你的所有笔记 （滚动查询）
     * @param max
     * @param offset
     * @return
     */
    @Override
    public ResultUtils queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱 （滚动分页查询）zrevrangebyscore key max min limit offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, 0, max, offset, 5);
        //非空判断
        if(typedTuples == null || typedTuples.isEmpty()) {
            return ResultUtils.ok();
        }

        //3.解析数据：blogId,score（时间戳）,offset
        //存放博客id的集合要指定大小，否则后面扩容会占内存
        List<Long> ids = new ArrayList<>(typedTuples.size());
        //这一次查询到的最小值
        long minTime = 0;
        //最小值出现的次数，最少为1
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //4.1获取博客id并添加到集合中
            ids.add(Long.valueOf(tuple.getValue()));
            //4.2获取分数（时间间隔）long类型
            long time = tuple.getScore().longValue();
            if(time == minTime) {
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        //4.得到blogId，根据id查询blog，直接用mp的listByIds会是顺序混乱
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogsList = query().in("id", ids).last("ORDER BY FIELD(ID," + idStr + ")").list();
        //不仅要查询博客，还要查询与博客相关的用户信息和点赞信息
        for (Blog blog : blogsList) {
            //查询blog有关用户
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        }
        //5.封装滚动分页查询参数并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogsList);
        r.setOffset(os);
        r.setMinTime(minTime);
        return ResultUtils.ok(r);
    }

    /**
     * 根据博客查询用户信息
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
