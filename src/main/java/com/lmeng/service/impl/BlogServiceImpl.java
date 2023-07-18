package com.lmeng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lmeng.constant.RedisConstants.BLOG_LIKED_KEY;
import static com.lmeng.constant.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
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

    @Override
    public ResultUtils queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
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

    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
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
     * 查询点赞排行榜
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
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + " )").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return ResultUtils.ok(userDTOS);
    }

    /**
     * 修改新增笔记业务，新增笔记后也能推送到粉丝收件箱
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
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //4.推送笔记作者所有粉丝
        for (Follow follow : follows) {
            //4.1获取粉丝id
            Long userId = follow.getUserId();
            //4.2推送，即将笔记保存到用户zset缓存集合中
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
                .rangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if(typedTuples == null || typedTuples.isEmpty()) {
            return ResultUtils.ok();
        }

        //3.解析数据：blogId,score（时间戳）,offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //4.1获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //4.2获取分数（时间间隔）
            long time = tuple.getScore().longValue();
            if(time == minTime) {
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        //4.得到blogId，根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(ID," + idStr + ")").list();

        for (Blog blog : blogs) {
            //查询blog有关用户
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        }

        //5.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return ResultUtils.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
