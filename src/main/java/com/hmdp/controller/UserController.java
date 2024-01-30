package com.hmdp.controller;


import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码

        // 验证手机号格式正确性
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("输入错误的手机号格式,请重新输入");
        }

        String code = RandomUtil.randomNumbers(6);
        log.info("code is {}", code);
        //  使用Redis进行数据的存储
        //  session.setAttribute(phone, code);
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        return Result.ok();
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {

        return userService.loginCheck(loginForm, session);
    }

    /**
     * 登出功能
     *
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout() {
        UserHolder.removeUser();
        return Result.ok("登出成功");
    }

    @GetMapping("/me")
    public Result me() {
        if (UserHolder.getUser() == null) {
            return Result.fail("用户未登录");
        }
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
