package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
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
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;


    /**
     * 判断是否关注
     * @Parameter [id]
     * @Return Result
     */
    @GetMapping("or/not/{id}")
    public Result judgeFollow(@PathVariable("id") Long id) {
        Boolean followed = followService.isFollow(id);
        return Result.ok(BooleanUtil.isTrue(followed));

    }

    /**
     * 关注/取关用户
     * @Parameter [id, isFollow]
     * @Return Result
     */
    @PutMapping("{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        if (isFollow) {
            return Result.ok(followService.follow(id));
        } else {
            return Result.ok(followService.unfollow(id));
        }
    }

    /**
     * 共同关注
     * @Parameter [id]
     * @Return Result
     */
    @GetMapping("common/{id}")
    public Result common(@PathVariable("id") Long id) {
        List<UserDTO> commonList = followService.getCommonList(id);
        return Result.ok(commonList);
    }
}
