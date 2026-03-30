package com.SpotsSips.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.SpotsSips.dto.UserDTO;
import com.SpotsSips.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.SpotsSips.utils.RedisConstants.LOGIN_USER_KEY;
import static com.SpotsSips.utils.RedisConstants.LOGIN_USER_TTL;
import static com.SpotsSips.utils.SystemConstants.TOKEN;

public class RefreshInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的token
        String token = request.getHeader(TOKEN);

        // 获取redis中保存的用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate
                .opsForHash().entries(key);

        // 查询用户是否存在
        if (userMap.isEmpty()) {
            // 不存在则放行，ThreadLocal中为null
            return true;
        }

        // 将userMap转换为userDTO
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 第二个参数传入一个新对象用于转换，第三个参数为是否忽略报错

        // 将用户信息保存到ThreadLocal中
        UserHolder.saveUser(user);

        // 刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
