package com.SpotsSips.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.SpotsSips.dto.Result;
import com.SpotsSips.dto.UserDTO;
import com.SpotsSips.entity.Follow;
import com.SpotsSips.entity.User;
import com.SpotsSips.mapper.FollowMapper;
import com.SpotsSips.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.SpotsSips.service.IUserService;
import com.SpotsSips.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result isFollow(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();

        return Result.ok(count >0);
    }

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;

        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean success = save(follow);

            if (success) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            boolean success = followMapper.removeFollow(userId, id);
            if (success) {
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result followCommons(Long id) {
        Long currentUserId = UserHolder.getUser().getId();
        Set<String> intersectStr = stringRedisTemplate.opsForSet().intersect("follows:" + id.toString(), "follows:" + currentUserId.toString());
        if (intersectStr == null || intersectStr.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Set<Long> intersectLong = intersectStr.stream().map(Long::valueOf).collect(Collectors.toSet());
        List<User> commons = userService.listByIds(intersectLong);
        List<UserDTO> commonsDTO = commons.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(commonsDTO);
    }
}
