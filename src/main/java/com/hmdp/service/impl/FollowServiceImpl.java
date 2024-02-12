package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Boolean isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 根据双主键查询结果
        Long count = query().eq("follow_user_id", id).eq("user_id", userId).count();
        return count > 0;
    }

    @Override
    public Boolean follow(Long id) {
        Follow follow = new Follow();
        Long userId = UserHolder.getUser().getId();
        follow.setUserId(userId);
        follow.setFollowUserId(id);
        stringRedisTemplate.opsForSet().add(RedisConstants.USER_FOLLOW_KEY + userId, String.valueOf(id));
        return save(follow);

    }

    @Override
    public Boolean unfollow(Long id) {
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        Long userId = UserHolder.getUser().getId();
        queryWrapper.eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id);
        stringRedisTemplate.opsForSet().remove(RedisConstants.USER_FOLLOW_KEY + userId, String.valueOf(id));
        return remove(queryWrapper);
    }

    @Override
    public List<UserDTO> getCommonList(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 获取当前用户的关注列表
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(RedisConstants.USER_FOLLOW_KEY + userId, RedisConstants.USER_FOLLOW_KEY + id);
        if (intersect == null || intersect.isEmpty())
            return Collections.emptyList();

        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        return listByIds(ids).stream().map((user -> BeanUtil.copyProperties(user, UserDTO.class))).collect(Collectors.toList());

    }
}
