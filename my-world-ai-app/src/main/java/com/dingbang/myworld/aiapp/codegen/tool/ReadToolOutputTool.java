package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;

import java.io.IOException;

/**
 * 只允许当前会话分页读取已经外置保存的命令输出。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public final class ReadToolOutputTool implements Tool<ReadToolOutputArgs> {
    /** 按可信会话隔离的输出存储。 */
    private final LocalToolOutputStore store;

    /**
     * 绑定可信输出目录。
     *
     * @param store 输出存储
     */
    public ReadToolOutputTool(LocalToolOutputStore store) { this.store = store; }

    /**
     * 返回参数类型。
     *
     * @return 回读参数类型
     */
    @Override
    public Class<ReadToolOutputArgs> parameterType() { return ReadToolOutputArgs.class; }

    /**
     * 返回模型可见工具描述。
     *
     * @return 工具描述
     */
    @Override
    public ToolDescriptor<ReadToolOutputArgs> descriptor() {
        return ToolDescriptor.of("read_tool_output", "按 outputId 和字节偏移分页读取本会话命令日志",
                ReadToolOutputArgs.class);
    }

    /**
     * 读取指定会话下的有限输出片段。
     *
     * @param args 模型参数
     * @param context 可信执行上下文
     * @return 包含下一偏移的文本片段
     * @throws IllegalStateException 存储读取失败时
     */
    @Override
    public ToolExecutionResult execute(ReadToolOutputArgs args, ToolExecutionContext context) {
        context.checkActive();
        /** 当前会话可访问的片段。 */
        LocalToolOutputStore.Chunk chunk;
        try {
            chunk = store.read(context.getSessionId(), args.outputId,
                    args.offset == null ? 0 : args.offset, args.limit == null ? 4096 : args.limit);
        } catch (IOException exception) {
            throw new IllegalStateException("命令输出读取失败", exception);
        }
        context.checkActive();
        return ToolExecutionResult.text("outputId=" + chunk.outputId() + " offset=" + chunk.offset()
                + " nextOffset=" + chunk.nextOffset() + " storedBytes=" + chunk.storedBytes()
                + "\n" + chunk.text());
    }
}
