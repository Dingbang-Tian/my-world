package com.dingbang.myworld.agent.api;

import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.Objects;

/**
 * 一次 Agent 运行的不可变最终结果。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
@Data
public final class AgentResult {

    /**
     * 本次运行标识。
     */
    private final String runId;

    /**
     * 所属会话标识。
     */
    private final String sessionId;

    /**
     * 调用方请求标识。
     */
    private final String requestId;

    /**
     * 运行终态。
     */
    private final AgentResultStatus status;

    /**
     * 完整助手回答；失败时为 null。
     */
    private final String finalText;

    /**
     * 模型结束原因；失败时可为 null。
     */
    private final ModelFinishReason finishReason;

    /**
     * 结构化错误；成功时为 null。
     */
    private final AgentError error;

    /**
     * 本轮使用的系统模板标识。
     */
    private final String promptTemplateId;

    /**
     * 本轮使用的系统模板内容哈希。
     */
    private final String promptHash;

    /**
     * 创建带有运行关联与模板版本信息的最终结果。
     *
     * @param runId 运行标识
     * @param sessionId 会话标识
     * @param requestId 请求标识
     * @param status 运行终态
     * @param finalText 完整回答，失败时为 null
     * @param finishReason 模型结束原因，失败时可为 null
     * @param error 结构化错误，成功时为 null
     * @param promptTemplateId 系统模板标识
     * @param promptHash 系统模板内容哈希
     * @throws IllegalArgumentException 成功与失败状态的数据不匹配时
     */
    public AgentResult(String runId, String sessionId, String requestId, AgentResultStatus status,
                       String finalText, ModelFinishReason finishReason, AgentError error,
                       String promptTemplateId, String promptHash) {
        this.runId = Objects.requireNonNull(runId, "运行标识不能为 null");
        this.sessionId = Objects.requireNonNull(sessionId, "会话标识不能为 null");
        this.requestId = Objects.requireNonNull(requestId, "请求标识不能为 null");
        this.status = Objects.requireNonNull(status, "运行状态不能为 null");
        if (status == AgentResultStatus.COMPLETED
                && (StringUtils.isBlank(finalText) || finishReason == null || error != null)) {
            throw new IllegalArgumentException("完成结果需要完整文本和模型结束原因，且不能包含错误");
        }
        if (status == AgentResultStatus.FAILED && (error == null || finalText != null)) {
            throw new IllegalArgumentException("失败结果需要错误信息，且不能包含最终回答");
        }
        this.finalText = finalText;
        this.finishReason = finishReason;
        this.error = error;
        this.promptTemplateId = Objects.requireNonNull(promptTemplateId, "模板标识不能为 null");
        this.promptHash = Objects.requireNonNull(promptHash, "模板哈希不能为 null");
    }
}
