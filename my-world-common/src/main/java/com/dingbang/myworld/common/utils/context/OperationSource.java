package com.dingbang.myworld.common.utils.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 操作来源枚举。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Getter
@RequiredArgsConstructor
public enum OperationSource {

    /**
     * 系统任务
     */
    SYSTEM(0),

    /**
     * 登录用户
     */
    USER(1);

    /**
     * 来源编码
     */
    private final int code;
}
