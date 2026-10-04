package com.dingbang.myworld.agent.persistence;

import com.dingbang.myworld.agent.orchestration.PlanStepStatus;
import com.dingbang.myworld.aiframework.model.ToolCall;
import lombok.Value;
import java.util.List;

/**
 * 保存可复用的计划步骤状态。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Value
public class PlanRecovery {
    /**
     * 原计划工具调用。
     */
    ToolCall call;
    /**
     * 按步骤顺序保存的状态。
     */
    List<PlanStepStatus> statuses;
    /**
     * 按步骤顺序保存的结果。
     */
    List<String> results;
}
