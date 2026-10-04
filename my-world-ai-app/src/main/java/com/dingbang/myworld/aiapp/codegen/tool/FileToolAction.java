package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import java.io.IOException;

/**
 * 文件操作的受检异常适配。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@FunctionalInterface
interface FileToolAction<P> {
    /**
     * 执行文件操作。
     *
     * @param args 工具参数
     * @param context 执行上下文
     * @return 工具输出
     * @throws IOException 文件系统操作失败时
     */
    ToolExecutionResult run(P args, ToolExecutionContext context) throws IOException;
}
