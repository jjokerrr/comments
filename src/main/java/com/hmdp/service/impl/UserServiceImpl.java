package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result loginCheck(LoginFormDTO loginFormDTO, HttpSession httpSession) {
        //  实现登录功能
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("输入错误的手机号格式,请重新输入");
        }

        String codeKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String code = stringRedisTemplate.opsForValue().get(codeKey);
//        String code = (String) httpSession.getAttribute(phone);
        if (StringUtils.isEmpty(code) || !code.equals(loginFormDTO.getCode())) {
            return Result.fail("验证码有误");
        }
        // mybatisplus 的工厂方法,用于创建一个QueryWrapper对象
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserwithPhone(phone);
        }
        stringRedisTemplate.delete(codeKey);

        // 创建token用于登录校验
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = user.toUserDto();
        log.info("当前登录的用户是{}", userDTO.toString());
        // 直接转换的话会出现无法将Long转换为String的异常，下面使用转换方法
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = new HashMap<>();
        BeanUtil.beanToMap(userDTO, userMap
                , CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String userKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(userKey, userMap);
        // 设置过期时间
        stringRedisTemplate.expire(userKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 删除session中的code字段
        // httpSession.removeAttribute(phone);
        // httpSession.setAttribute("user",user);
        // 登录成功，返回token
        return Result.ok(token);
    }

    private User createUserwithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        String nickName = SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10);
        user.setNickName(nickName);
        save(user);
        return user;
    }
}
