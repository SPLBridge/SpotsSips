package com.SpotsSips.service;

import com.SpotsSips.dto.Result;
import com.SpotsSips.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result isFollow(Long id);

    Result follow(Long id, Boolean isFollow);

    Result followCommons(Long id);
}
