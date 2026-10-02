package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

/**
 * 对 SDK 调用方可见的结构化 Agent 错误。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class AgentError {

    /**
     * 稳定的错误类别代码。
     */
    private final String code;

    /**
     * 便于定位问题的错误说明。
     */
    private final String message;

    /**
     * 创建错误信息。
     *
     * @param code 错误类别代码
     * @param message 错误说明
     * @throws IllegalArgumentException 代码或说明为空时
     */
    public AgentError(String code, String message) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("错误代码和说明不能为空");
        }
        this.code = code;
        this.message = message;
    }
}
