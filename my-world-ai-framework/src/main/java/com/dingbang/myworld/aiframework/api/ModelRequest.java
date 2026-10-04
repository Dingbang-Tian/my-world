package com.dingbang.myworld.aiframework.api;

import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.common.utils.collection.CollectionUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一次模型请求的最小不可变输入快照。
 *
 * @author Sebastian
 * @since 2026/10/01
 */
@Data
public final class ModelRequest {

    /**
     * 已配置的模型实例标识。
     */
    private final String modelId;

    /**
     * 本轮发送给模型的消息快照。
     */
    private final List<Message> messages;

    /**
     * 本轮允许模型调用的工具说明。
     */
    private final List<ModelToolDefinition> tools;

    /**
     * 本次调用的可选覆盖值。
     */
    private final ModelOptions options;

    /**
     * 本次调用的截止时间、取消信号和响应预算。
     */
    private final ModelExecutionContext executionContext;

    /**
     * 校验模型标识并复制消息列表。
     *
     * @param modelId 已配置的模型实例标识
     * @param messages 本轮消息列表
     * @throws IllegalArgumentException 当模型标识或消息列表为空时
     * @throws NullPointerException 当消息列表为 null 时
     */
    public ModelRequest(String modelId, List<Message> messages) {
        this(modelId, messages, Collections.emptyList());
    }

    /**
     * 校验并复制消息及模型可见工具说明。
     *
     * @param modelId 已配置的模型实例标识
     * @param messages 本轮消息列表
     * @param tools 本轮授权工具说明
     */
    public ModelRequest(String modelId, List<Message> messages, List<ModelToolDefinition> tools) {
        this(modelId, messages, tools, ModelOptions.empty());
    }

    /**
     * 校验并复制完整请求，不修改共享模型配置。
     *
     * @param modelId 已配置的模型实例标识
     * @param messages 本轮消息列表
     * @param tools 本轮授权工具说明
     * @param options 本次调用的选项覆盖值
     */
    public ModelRequest(String modelId, List<Message> messages, List<ModelToolDefinition> tools,
                        ModelOptions options) {
        this(modelId, messages, tools, options, ModelExecutionContext.defaults());
    }

    /**
     * 固定完整请求与运行级执行边界。
     *
     * @param modelId 模型标识
     * @param messages 消息快照
     * @param tools 授权工具
     * @param options 本次选项
     * @param executionContext 截止时间、取消与输出保护
     */
    public ModelRequest(String modelId, List<Message> messages, List<ModelToolDefinition> tools,
                        ModelOptions options, ModelExecutionContext executionContext) {
        if (StringUtils.isBlank(modelId)) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        Objects.requireNonNull(messages, "消息列表不能为 null");
        if (CollectionUtils.isEmpty(messages)) {
            throw new IllegalArgumentException("模型请求至少需要一条消息");
        }
        messages.forEach(Objects::requireNonNull);
        this.modelId = modelId;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        Objects.requireNonNull(tools, "工具说明列表不能为 null").forEach(Objects::requireNonNull);
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
        this.options = Objects.requireNonNull(options, "模型选项不能为 null");
        this.executionContext = Objects.requireNonNull(executionContext, "执行上下文不能为 null");
    }

}
