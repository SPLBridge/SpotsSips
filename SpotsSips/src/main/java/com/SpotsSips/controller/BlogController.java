package com.SpotsSips.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.SpotsSips.dto.Result;
import com.SpotsSips.dto.UserDTO;
import com.SpotsSips.entity.Blog;
import com.SpotsSips.service.IBlogService;
import com.SpotsSips.utils.SystemConstants;
import com.SpotsSips.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

import static com.SpotsSips.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.like(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result likes(@PathVariable("id") Long id) {
        return blogService.likes(id);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id
    ) {
        Page<Blog> page = blogService
                .query()
                .eq("user_id", id)
                .page(new Page<>(current, MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();

        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result blogOfFollow(@RequestParam("lastId") Long max,
                               @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.blogOfFollow(max, offset);
    }
}
