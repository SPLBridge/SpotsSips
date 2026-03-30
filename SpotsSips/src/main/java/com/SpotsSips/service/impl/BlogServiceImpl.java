package com.SpotsSips.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.SpotsSips.dto.Result;
import com.SpotsSips.dto.ScrollResult;
import com.SpotsSips.dto.UserDTO;
import com.SpotsSips.entity.Blog;
import com.SpotsSips.entity.Follow;
import com.SpotsSips.entity.User;
import com.SpotsSips.mapper.BlogMapper;
import com.SpotsSips.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.SpotsSips.service.IFollowService;
import com.SpotsSips.service.IUserService;
import com.SpotsSips.utils.SystemConstants;
import com.SpotsSips.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.SpotsSips.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.SpotsSips.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;

    @Autowired
    private IFollowService followService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id);

        // 检查blog是否存在
        if (blog == null) {
            return Result.fail("blog不存在");
        }

        // 查询用户
        Long userId = blog.getUserId();

        // 获取用户
        User user = userService.getById(userId);

        // 设置icon和name
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser != null) {
            blog.setIsLike(liked(BLOG_LIKED_KEY + id, currentUser.getId()));
        }

        return Result.ok(blog);
    }

    @Override
    public Result like(Long id) {
        // 获取key和用户id
        String key = BLOG_LIKED_KEY + id;
        Long userId = UserHolder.getUser().getId();

        // 检查是否点过赞
        if (liked(key, userId)) {
            // 点过，取消点赞
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            update().setSql("liked = liked - 1").eq("id", id).update();
            return Result.ok();
        }

        // 没点过，点赞
        stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        update().setSql("liked = liked + 1").eq("id", id).update();

        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::setBlog);
        return Result.ok(records);
    }

    @Override
    public Result likes(Long id) {
        // 获取key
        String key = BLOG_LIKED_KEY + id;

        // 获取前5个用户
        Set<String> likesIdStr = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (likesIdStr == null || likesIdStr.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> likesId = likesIdStr
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        String joined = StrUtil.join(", ", likesId);

        List<User> likes = userService
                .query()
                .in("id", likesId)
                .last("order by field (id, " + joined + ")")
                .list();

        List<UserDTO> likesDTO = likes
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(likesDTO);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);

        // 保存探店博文
        save(blog);

        // 查询作者所有粉丝
        List<Follow> followers = followService.query().eq("follow_user_id", userId).list();

        // 推送给粉丝
        Long blogId = blog.getId();
        for (Follow follower : followers) {
            stringRedisTemplate.opsForZSet().add(
                    FEED_KEY + follower.getUserId(), blogId.toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blogId);
    }

    @Override
    public Result blogOfFollow(Long max, Integer offset) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 获取收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_KEY + userId, 0, max, offset, 3);

        // 检查是否为空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 获取数据
        long minTime = System.currentTimeMillis();
        int nextOffset = 1;
        ArrayList<String> blogIds = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            blogIds.add(typedTuple.getValue());
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (time < minTime) {
                minTime = time;
                nextOffset = 1;
            } else if (time == minTime) {
                nextOffset++;
            }
        }

        // 查询blog数据
        String joinedBlogIds = StrUtil.join(", ", blogIds);
        List<Blog> blogs = query().in("id", blogIds)
                .last("order by field (id, " + joinedBlogIds + ")").list();

        blogs.forEach(this::setBlog);

        ScrollResult scrollResult = new ScrollResult(blogs, minTime, nextOffset);

        return Result.ok(scrollResult);
    }

    private boolean liked(String key, Long userId) {
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        return score != null;
    }

    private void setBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser != null) {
            blog.setIsLike(liked(BLOG_LIKED_KEY + blog.getId(), currentUser.getId()));
        }
    }

}
