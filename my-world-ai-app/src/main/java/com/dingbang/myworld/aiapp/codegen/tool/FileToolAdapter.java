package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolDescriptor;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

/**
 * 将文件操作包装为模型可调用的工具。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class FileToolAdapter<P> implements Tool<P> {
    /**
     * 工具名称。
     */
    private final String name;
    /**
     * 工具说明。
     */
    private final String description;
    /**
     * 工具参数类型。
     */
    private final Class<P> type;
    /**
     * 实际文件操作。
     */
    private final FileToolAction<P> action;

    /**
     * 返回参数类型。
     *
     * @return 参数类型
     */
    @Override
    public Class<P> parameterType() {
        return type;
    }

    /**
     * 返回模型可见的工具说明。
     *
     * @return 工具描述
     */
    @Override
    public ToolDescriptor<P> descriptor() {
        return ToolDescriptor.of(name, description, type);
    }

    /**
     * 标记会修改工作目录的操作。
     *
     * @return 有外部副作用时为真
     */
    @Override
    public boolean mayHaveExternalSideEffects() {
        return "create_file".equals(name) || "edit_file".equals(name)
                || "move_file".equals(name) || "delete_file".equals(name);
    }

    /**
     * 在执行边界内调用文件操作。
     *
     * @param parameters 已校验参数
     * @param context 执行上下文
     * @return 文件操作结果
     */
    @Override
    public ToolExecutionResult execute(P parameters, ToolExecutionContext context) {
        try {
            context.checkActive();
            return action.run(parameters, context);
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件操作失败: " + exception.getMessage(), exception);
        }
    }
}
