package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Blog queryById(Long id) {
        Blog blog = this.getById(id);
        queryBlogUser(blog);
        queryBlogLiked(blog);
        return blog;
    }

    private void queryBlogLiked(Blog blog) {
        Long id = blog.getId();
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKE_KEY + id, String.valueOf(userId));
        Boolean liked = score != null && score != 0;
        blog.setIsLike(BooleanUtil.isTrue(liked));
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public List<Blog> queryBlogList(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            queryBlogLiked(blog);
        });
        return records;
    }

    @Override
    public void likeBlog(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 检查是否已经点赞过
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKE_KEY + id, String.valueOf(userId));
        Boolean liked = score != null && score != 0;
        // 未点赞，那么更新redis且更新数据库
        if (BooleanUtil.isFalse(liked)) {
            boolean success = this.update().setSql("liked = liked + 1").eq("id", id).update();
            if (success)
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKE_KEY + id, String.valueOf(userId), System.currentTimeMillis());
        } else {
            // 已点赞，那么修改点赞状态更新redis且更新数据库
            boolean success = this.update().setSql("liked = liked - 1").eq("id", id).update();
            if (success)
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKE_KEY + id, String.valueOf(userId));
        }

    }

    /**
     * 查询点赞前五用户
     * @Parameter [blogId]
     * @Return UserDTO>
     */
    @Override
    public List<UserDTO> queryLikes(Long blogId) {
        // 在Redis中查询对应用户
        Set<String> userSet = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKE_KEY + blogId, 0, 4);
        // 判断用户集合是否为空
        if (userSet == null || userSet.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userList = userSet.stream().map(Long::valueOf).collect(Collectors.toList());

        return userService.listByIds(userList).stream().map((user ->
                BeanUtil.copyProperties(user, UserDTO.class))).collect(Collectors.toList());
    }

}
