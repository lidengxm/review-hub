package com.lmeng.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lmeng.dto.ResultUtils;
import com.lmeng.dto.UserDTO;
import com.lmeng.model.Blog;
import com.lmeng.service.IBlogService;
import com.lmeng.service.IUserService;
import com.lmeng.constant.SystemConstants;
import com.lmeng.global.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserService userService;

    @PostMapping
    public ResultUtils saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public ResultUtils likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量 update tb_blog set liked = liked + 1 where id = ?
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public ResultUtils queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return ResultUtils.ok(records);
    }

    @GetMapping("/hot")
    public ResultUtils queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public ResultUtils queryBlogById(@PathVariable("id") Long id) {
        //查看探店发布笔记图文
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public ResultUtils queryBlogLikes(@PathVariable("id") Long id) {
        //点赞笔记
        return blogService.queryBlogLikes(id);
    }

    /**
     * 根据用户id查询user
     * @param current
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public ResultUtils queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return ResultUtils.ok(records);
    }

    @GetMapping("/of/follow")
    public ResultUtils queryBlogOfFollow(@RequestParam("lastId") Long max,
                         @RequestParam(value = "offset",defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(max,offset);
    }
}
