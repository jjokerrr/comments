package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

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


    @GetMapping("or/not/{id}")
    public Result judgeFollow(@PathVariable("id") Long id) {
        Boolean followed = followService.isFollow(id);
        return Result.ok(BooleanUtil.isTrue(followed));

    }

    @PutMapping("{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        if (isFollow) {
            return Result.ok(followService.follow(id));
        } else {
            return Result.ok(followService.unfollow(id));
        }
    }
}
