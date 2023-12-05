package com.hmdp.interceptor;

import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class LoginCheckInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");
        System.out.println(user);
        if (user == null) {
            log.error("用户未登录");
            response.setStatus(401);
            return false;
        }
        // 不能将保存有用户敏感信息的User对象返回给前端，这里应该将User封装成UserDto返回
        UserHolder.saveUser(user.toUserDto());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 在结束执行方法之后，应该将UserHolder进行删除，避免内存泄露
        UserHolder.removeUser();
    }
}
