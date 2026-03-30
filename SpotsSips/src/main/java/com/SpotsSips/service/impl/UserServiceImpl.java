package com.SpotsSips.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.SpotsSips.dto.LoginFormDTO;
import com.SpotsSips.dto.Result;
import com.SpotsSips.dto.UserDTO;
import com.SpotsSips.entity.User;
import com.SpotsSips.mapper.UserMapper;
import com.SpotsSips.service.IUserService;
import com.SpotsSips.utils.SystemConstants;
import com.SpotsSips.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.SpotsSips.utils.RedisConstants.*;
import static com.SpotsSips.utils.RegexUtils.isPhoneInvalid;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (isPhoneInvalid(phone)) {
            // 不符合，返回失败
            return Result.fail("请输入正确的手机号");
        }

        // 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

//        // 保存验证码&手机号到session
//        session.setAttribute(SystemConstants.CODE, code);
//        session.setAttribute(SystemConstants.PHONE, phone);

        // 以手机号为key，保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 发送验证码
        // 保存到日志中，模拟实际发送验证码
        log.debug("发送验证码 " + code);

        return Result.ok();
    }

//    @Override
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        // 获取会话中保存的手机号和验证码对象，如果为null，则说明未保存过
//        Object phoneObj = session.getAttribute(SystemConstants.PHONE);
//        Object codeObj = session.getAttribute(SystemConstants.CODE);
//
//        // 检查是否获取验证码
//        if (phoneObj == null || codeObj == null) {
//            return Result.fail("请点击\"发送验证码\"以获取验证码");
//        }
//
//        // 获取会话中保存的手机号和验证码
//        String phone = phoneObj.toString();
//        String code = codeObj.toString();
//
//        // 校验手机号
//        if (!phone.equals(loginForm.getPhone())) {
//            return Result.fail("请输入正确的手机号");
//        }
//
//        // 校验验证码
//        if (!code.equals(loginForm.getCode())) {
//            // 不一致，登录失败
//            return Result.fail("验证码错误");
//        }
//
//        // 一致，检查用户是否存在
//        User user = query().eq(SystemConstants.PHONE, phone).one();
//        if (user == null) {
//            user = User.newUserWithPhone(phone);
//            save(user);
//        }
//
//        // 将用户信息保存到session中
//        session.setAttribute(SystemConstants.USER, BeanUtil.copyProperties(user, UserDTO.class));
//
//        return Result.ok();
//    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 通过提交表单获取手机号和提交的验证码
        String phone = loginForm.getPhone();
        String loginCode = loginForm.getCode();

        // 获取Redis中存储的手机号对应的验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        // 检查两个验证码是否一致
        if (!loginCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 一致，检查用户是否存在
        User user = query().eq(SystemConstants.PHONE, phone).one();
        if (user == null) {
            user = User.newUserWithPhone(phone);
            save(user);
        }

        // 将用户信息保存到Redis中
        // 1. 随机生成一个UUID作为登录token
        String token = UUID.randomUUID().toString(true);

        // 2. 将用户信息脱敏后转换成一个HashMap
//        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class));
        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class),
                new HashMap<>(),
                CopyOptions.create()
                        .ignoreNullValue()
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 3. 用token作为key，将用户信息存储到Redis中并设置有效期
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key, userMap);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 4. 返回token
        return Result.ok(token);
    }

    @Override
    public Result me() {
        return Result.ok(UserHolder.getUser());
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
