package com.SpotsSips.interceptor;

import com.SpotsSips.dto.UserDTO;
import com.SpotsSips.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取当前会话
//        HttpSession session = request.getSession();
//
//        // 获取会话中保存的用户
//        Object user = session.getAttribute(SystemConstants.USER);
//
//        // 检查用户是否存在
//        if (user == null) {
//            // 不存在，设置状态码为401，返回false（不放行）
//            response.setStatus(401);
//        }
//
//        // 存在，将用户存储到ThreadLocal中并放行
//        UserHolder.saveUser((UserDTO) user);
//        return true;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取请求头中的token
//        String token = request.getHeader(TOKEN);
//
//        // 获取redis中保存的用户
//        String key = LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate
//                .opsForHash().entries(key);
//
//        // 查询用户是否存在
//        if (userMap.isEmpty()) {
//            // 不存在则拦截并返回401状态码
//            response.setStatus(401);
//            return false;
//        }
//
//        // 将userMap转换为userDTO
//        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        // 第二个参数传入一个新对象用于转换，第三个参数为是否忽略报错
//
//        // 将用户信息保存到ThreadLocal中
//        UserHolder.saveUser(user);
//
//        // 刷新token有效期
//        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
//
//        // 放行
//        return true;
        // 从ThreadLocal中获取用户信息
        UserDTO user = UserHolder.getUser();

        // 检查用户是否存在
        if (user == null) {
            // 不存在则阻塞
            response.setStatus(401);
            return false;
        }

        // 放行
        return true;
    }
}
