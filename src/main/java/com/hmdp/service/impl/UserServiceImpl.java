package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import net.sf.jsqlparser.util.validation.metadata.NamedObject;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import javax.servlet.http.HttpSession;

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
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result loginCheck(LoginFormDTO loginFormDTO, HttpSession httpSession) {
        //  实现登录功能
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("输入错误的手机号格式,请重新输入");
        }


        String code = (String) httpSession.getAttribute(phone);
        if (StringUtils.isEmpty(code) || !code.equals(loginFormDTO.getCode())) {
            return Result.fail("验证码有误");
        }
        // mybatisplus 的工厂方法,用于创建一个QueryWrapper对象
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserwithPhone(phone);
        }
        httpSession.setAttribute("user",user);
        return Result.ok(user);
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
