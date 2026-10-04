package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在抛出异常前模拟已发生外部修改的测试工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class JdbcPersistenceRecoveryTestRiskyTool implements Tool<JdbcPersistenceRecoveryTestRiskyArgs> {
    /**
     * 模拟副作用标记。
     */
    private final AtomicBoolean changed;

    /**
     * 绑定副作用标记。
     *
     * @param changed 副作用状态
     */
    JdbcPersistenceRecoveryTestRiskyTool(AtomicBoolean changed) { this.changed = changed; }

    /**
     * @return 工具参数类
     */
    @Override public Class<JdbcPersistenceRecoveryTestRiskyArgs> parameterType() { return JdbcPersistenceRecoveryTestRiskyArgs.class; }
    /**
     * @return 可信工具描述
     */
    @Override public ToolDescriptor<JdbcPersistenceRecoveryTestRiskyArgs> descriptor() {
        return ToolDescriptor.of("risky_write", "模拟写入后失败", JdbcPersistenceRecoveryTestRiskyArgs.class);
    }
    /**
     * @return 工具有外部副作用时为 true
     */
    @Override public boolean mayHaveExternalSideEffects() { return true; }
    /**
     * 修改状态后模拟写入失败。
     *
     * @param parameters 已校验参数
     * @param context 运行上下文
     * @return 不会正常返回
     */
    @Override public ToolExecutionResult execute(JdbcPersistenceRecoveryTestRiskyArgs parameters, ToolExecutionContext context) {
        changed.set(true);
        throw new IllegalStateException("写入后异常");
    }
}
