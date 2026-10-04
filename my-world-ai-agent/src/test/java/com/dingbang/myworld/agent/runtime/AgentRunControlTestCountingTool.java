package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证取消后不再执行同批其余工具的测试工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@ToolInfo(name = "count", description = "统计工具调用")
public final class AgentRunControlTestCountingTool implements Tool<AgentRunControlTestEmptyArgs> {
    /**
     * 实际执行次数。
     */
    private final AtomicInteger calls;
    /**
     * 首次开始的同步标记，可为空。
     */
    private final CountDownLatch entered;
    /**
     * 是否等待取消。
     */
    private final boolean waitForCancel;

    /**
     * 绑定计数与等待策略。
     *
     * @param calls 执行计数
     * @param entered 首次执行标记，可为空
     * @param waitForCancel 是否等待取消
     */
    public AgentRunControlTestCountingTool(AtomicInteger calls, CountDownLatch entered, boolean waitForCancel) {
        this.calls = calls;
        this.entered = entered;
        this.waitForCancel = waitForCancel;
    }

    /**
     * 返回无参数工具类型。
     *
     * @return 参数类型
     */
    @Override
    public Class<AgentRunControlTestEmptyArgs> parameterType() {
        return AgentRunControlTestEmptyArgs.class;
    }

    /**
     * 执行一次并在需要时等待协作取消。
     *
     * @param parameters 空参数
     * @param context 可信工具上下文
     * @return 工具执行结果
     */
    @Override
    public ToolExecutionResult execute(AgentRunControlTestEmptyArgs parameters, ToolExecutionContext context) {
        calls.incrementAndGet();
        if (entered != null) {
            entered.countDown();
        }
        if (waitForCancel) {
            while (!context.getCancellation().isCancelled()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return ToolExecutionResult.text("1");
    }
}
