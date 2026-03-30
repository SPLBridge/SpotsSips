package com.SpotsSips.service;

import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result like(Long id);

    Result queryHotBlog(Integer current);

    Result likes(Long id);

    Result saveBlog(Blog blog);

    Result blogOfFollow(Long max, Integer offset);
}
