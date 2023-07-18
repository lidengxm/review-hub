package com.hmdp.service;

import com.hmdp.dto.ResultUtils;
import com.hmdp.model.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    ResultUtils queryHotBlog(Integer current);

    ResultUtils queryBlogById(Long id);

    ResultUtils likeBlog(Long id);

    ResultUtils queryBlogLikes(Long id);

    ResultUtils saveBlog(Blog blog);

    ResultUtils queryBlogOfFollow(Long max, Integer offset);
}
