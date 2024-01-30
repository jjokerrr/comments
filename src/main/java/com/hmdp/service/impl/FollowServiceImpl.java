package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
        follow.setUserId(UserHolder.getUser().getId());
        follow.setFollowUserId(id);
        return save(follow);

    }

    @Override
    public Boolean unfollow(Long id) {
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getUserId, UserHolder.getUser().getId()).eq(Follow::getFollowUserId, id);
        return remove(queryWrapper);
    }
}
