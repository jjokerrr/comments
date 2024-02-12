package com.hmdp.controller;


import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

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

    /**
     * 保存博客并推送用户
     *
     * @Parameter [blog]
     * @Return Result
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        Boolean isSuccess = blogService.saveBlog(blog);
        if (BooleanUtil.isFalse(isSuccess)) return Result.fail("保存失败");
        return Result.ok(blog.getId());
    }

    /**
     * 点赞博客
     *
     * @Parameter [id]
     * @Return Result
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        blogService.likeBlog(id);
        return Result.ok();
    }

    /**
     * 个人主页查询
     *
     * @Parameter [current]
     * @Return Result
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId())
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询用户博客列表
     *
     * @Parameter [current, id]
     * @Return Result
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 主页面博客热度排行
     *
     * @Parameter [current]
     * @Return Result
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> records = blogService.queryBlogList(current);
        return Result.ok(records);
    }

    /**
     * 查看Blog详情页
     *
     * @Parameter [id]
     * @Return Result
     */
    @GetMapping("{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        Blog blog = blogService.queryById(id);
        if (blog == null) {
            return Result.fail("查询失败");
        }
        return Result.ok(blog);
    }

    /**
     * 查看博客点赞用户列表
     * @Parameter [blogId]
     * @Return Result
     */
    @GetMapping("likes/{id}")
    public Result queryLikes(@PathVariable("id") Long blogId) {
        List<UserDTO> userDTOS = blogService.queryLikes(blogId);
        return Result.ok(userDTOS);
    }

    /**
     * 查询关注用户发布博客
     * @Parameter [max, offset]
     * @Return Result
     */
    @GetMapping("of/follow")
    public Result queryFollowBlog(@RequestParam("lastId") Long max,@RequestParam(value = "offset",defaultValue = "0") Integer offset){
        ScrollResult scrollResult= blogService.queryFollowBlog(max,offset);
        return Result.ok(scrollResult);
    }

}
