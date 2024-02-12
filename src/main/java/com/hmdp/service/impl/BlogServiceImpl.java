package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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

    @Resource
    private IFollowService followService;

    @Override
    public Blog queryById(Long id) {
        Blog blog = this.getById(id);
        queryBlogUser(blog);
        queryBlogLiked(blog);
        return blog;
    }

    /**
     * 查询用户点赞状态，如果点赞则修改Blog中的点赞状态标志
     *
     * @Parameter [blog]
     * @Return
     */
    private void queryBlogLiked(Blog blog) {
        Long id = blog.getId();
        UserDTO user = UserHolder.getUser();
        if (user == null) return;
        Long userId = user.getId();
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
     *
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

    /**
     * 发布博客并推送到粉丝收件箱
     *
     * @Parameter [blog]
     * @Return Boolean
     */
    @Override
    public Boolean saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if (!save) return false;

        // 查询全部的粉丝用户
        List<Follow> fansList = followService.query()
                .eq("follow_user_id", user.getId())
                .list();
        for (Follow follow : fansList) {
            Long fansId = follow.getUserId();
            // 将博客id推送到用户信箱中
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + fansId, String.valueOf(blog.getId()), System.currentTimeMillis());
        }
        return true;

    }

    @Override
    public ScrollResult queryFollowBlog(Long max, Integer offset) {
        // 从Redis中查询用户的关注列表
        Long userId = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, offset, 3);
        // 查询为空，返回空结果
        if (typedTuples == null || typedTuples.isEmpty()) return new ScrollResult();

        List<Long> blogList = new ArrayList<>(typedTuples.size());
        // 解析数据，获取blogId，minTime offset;
        int ofs = 1;
        long minTime = 0;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 添加结果数组
            String value = tuple.getValue();
            blogList.add(Long.valueOf(value));

            // 更新偏移和最小时间
            Long score = tuple.getScore().longValue();
            if (minTime == score) {
                ofs++;
            } else {
                minTime = score;
                ofs = 1;
            }
        }

        // 查询对应的博客详情
        String ids = StrUtil.join(",", blogList);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + ids + ")").list();

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(ofs);
        scrollResult.setMinTime(minTime);

        return scrollResult;

    }

}
