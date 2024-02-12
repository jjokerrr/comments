package com.hmdp.service;

import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Blog queryById(Long id);

    List<Blog> queryBlogList(Integer current);

    void likeBlog(Long id);

    List<UserDTO> queryLikes(Long blogId);

    Boolean saveBlog(Blog blog);

    ScrollResult queryFollowBlog(Long max, Integer offset);
}
