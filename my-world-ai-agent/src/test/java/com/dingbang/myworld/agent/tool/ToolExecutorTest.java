package com.dingbang.myworld.agent.tool;

import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证工具授权、参数契约、执行结果和事件隔离。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
class ToolExecutorTest {
    /**
     * 成功工具只在参数校验后执行，并按调用标识发布阶段事件。
     */
    @Test
    void executesAnnotatedToolWithTrustedContext() {
        // 接收阶段事件。
        List<ToolExecutionPhase> phases = new ArrayList<>();
        // 受信任运行上下文。
        ToolExecutionContext context = new ToolExecutionContext("run-1", "session-1", Path.of("/tmp"), null);
        // 测试工具。
        AddTool tool = new AddTool();
        // 注册后的授权执行器。
        ToolExecutor executor = new ToolExecutor(new ToolRegistry(Collections.singletonList(tool)));

        // 与模型调用配对的结果。
        ToolResult result = executor.execute(new ToolCall("call-1", "add", "{\"a\":2,\"b\":3}"),
                context, event -> phases.add(event.getPhase()));

        assertThat(result.getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.getCallId()).isEqualTo("call-1");
        assertThat(result.getContent()).isEqualTo("5@run-1");
        assertThat(phases).containsExactly(ToolExecutionPhase.PREPARING,
                ToolExecutionPhase.CALLING, ToolExecutionPhase.COMPLETED);
        assertThat(tool.invocations).isEqualTo(1);
        // 模型可见的参数描述。
        JsonNode schema = tool.descriptor().getParameterSchema();
        assertThat(schema.get("required").size()).isEqualTo(2);
        assertThat(schema.get("properties").get("a").get("type").asText()).isEqualTo("integer");
        assertThat(schema.has("runId")).isFalse();
    }

    /**
     * 缺失、类型错误及未知参数均不得触发 Java 工具副作用。
     */
    @Test
    void rejectsInvalidArgumentsBeforeCallingTool() {
        // 测试工具。
        AddTool tool = new AddTool();
        // 授权执行器。
        ToolExecutor executor = new ToolExecutor(new ToolRegistry(Collections.singletonList(tool)));
        // 执行上下文。
        ToolExecutionContext context = new ToolExecutionContext("run", "session", null, null);
        for (String json : Arrays.asList("{\"a\":2}", "{\"a\":\"2\",\"b\":3}",
                "{\"a\":2,\"b\":3,\"runId\":\"fake\"}", "[]", "not-json")) {
            // 单次参数错误结果。
            ToolResult result = executor.execute(new ToolCall("call", "add", json), context, null);
            assertThat(result.getStatus()).isEqualTo(ToolResultStatus.ERROR);
            assertThat(result.getErrorCode()).isEqualTo("TOOL_VALIDATION_ERROR");
        }
        assertThat(tool.invocations).isZero();
    }

    /**
     * 未授权工具与同名不同实例都不能绕过注册边界。
     */
    @Test
    void rejectsUnknownAndConflictingTools() {
        // 单个工具的注册表。
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(new AddTool()));
        // 空授权快照上的执行器。
        ToolExecutor executor = new ToolExecutor(registry.select(Collections.emptyList()));
        // 未获授权的结果。
        ToolResult result = executor.execute(new ToolCall("call", "add", "{\"a\":2,\"b\":3}"),
                new ToolExecutionContext("run", "session", null, null), null);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_NOT_FOUND");
        assertThatThrownBy(() -> new ToolRegistry(Arrays.asList(new AddTool(), new AddTool())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("工具名称冲突");
    }

    /**
     * Java 工具异常与监听器异常不改变主结果。
     */
    @Test
    void isolatesListenerFailureAndReportsToolFailure() {
        // 抛错工具的执行器。
        ToolExecutor executor = new ToolExecutor(new ToolRegistry(Collections.singletonList(new FailingTool())));
        // 执行上下文。
        ToolExecutionContext context = new ToolExecutionContext("run", "session", null, null);
        // 异常不会中断的工具结果。
        ToolResult result = executor.execute(new ToolCall("call", "fail", "{}"), context,
                event -> { throw new IllegalStateException("observer failed"); });
        assertThat(result.getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_EXECUTION_ERROR");
    }

    /**
     * 列表与枚举 Schema 应与实际参数校验一致。
     */
    @Test
    void validatesEnumAndListElements() {
        // 枚举列表工具的描述。
        ToolDescriptor<FilterArgs> descriptor = new FilterTool().descriptor();
        // 列表字段 Schema。
        JsonNode schema = descriptor.getParameterSchema();
        assertThat(schema.get("properties").get("labels").get("items").get("type").asText())
                .isEqualTo("string");
        assertThat(schema.get("properties").get("mode").get("enum").toString())
                .isEqualTo("[\"FAST\",\"SLOW\"]");
        assertThat(descriptor.parse("{\"labels\":[\"one\"],\"mode\":\"FAST\"}").labels)
                .containsExactly("one");
        assertThatThrownBy(() -> descriptor.parse("{\"labels\":[1],\"mode\":\"FAST\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> descriptor.parse("{\"labels\":[\"one\"],\"mode\":\"OTHER\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 未支持的嵌套对象不能生成误导模型的 Schema。
     */
    @Test
    void rejectsUnsupportedNestedParameter() {
        assertThatThrownBy(() -> ToolDescriptor.of("nested", "嵌套参数", NestedArgs.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持");
    }

    /**
     * 显式描述入口与字符串、浮点、布尔、整数列表采用相同校验规则。
     */
    @Test
    void supportsExplicitDescriptorAndScalarTypes() {
        // 无工具类注解的显式描述工具。
        ExplicitTool tool = new ExplicitTool();
        // 工具描述的参数 Schema。
        JsonNode schema = tool.descriptor().getParameterSchema();
        assertThat(schema.get("properties").get("title").get("type").asText()).isEqualTo("string");
        assertThat(schema.get("properties").get("weight").get("type").asText()).isEqualTo("number");
        assertThat(schema.get("properties").get("enabled").get("type").asText()).isEqualTo("boolean");
        assertThat(schema.get("properties").get("offsets").get("items").get("type").asText())
                .isEqualTo("integer");
        // 显式工具的授权执行器。
        ToolExecutor executor = new ToolExecutor(new ToolRegistry(Collections.singletonList(tool)));
        // 执行上下文。
        ToolExecutionContext context = new ToolExecutionContext("run", "session", null, null);
        // 合法参数调用的结果。
        ToolResult result = executor.execute(new ToolCall("call", "explicit",
                "{\"title\":\"x\",\"weight\":1.5,\"enabled\":true,\"offsets\":[2,3]}"), context, null);
        assertThat(result.getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.getContent()).isEqualTo("x:2");
        assertThat(executor.execute(new ToolCall("bad", "explicit",
                "{\"title\":\"x\",\"weight\":1.5,\"enabled\":\"true\",\"offsets\":[2]}"),
                context, null).getErrorCode()).isEqualTo("TOOL_VALIDATION_ERROR");
    }

    /**
     * 两个整数参数。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public static class AddArgs {
        /**
         * 第一个加数。
         */
        @ToolParam(description = "第一个加数")
        private int a;
        /**
         * 第二个加数。
         */
        @ToolParam(description = "第二个加数")
        private int b;
    }

    /**
     * 测试用加法工具。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    @ToolInfo(name = "add", description = "计算两个整数之和")
    public static class AddTool implements Tool<AddArgs> {
        /**
         * 已实际调用次数。
         */
        private int invocations;

        /**
         * 返回参数类型。
         *
         * @return 加法参数类
         */
        @Override
        public Class<AddArgs> parameterType() {
            return AddArgs.class;
        }

        /**
         * 计算整数之和并显示可信运行标识。
         *
         * @param parameters 两个加数
         * @param context 可信运行上下文
         * @return 计算结果
         */
        @Override
        public ToolExecutionResult execute(AddArgs parameters, ToolExecutionContext context) {
            invocations++;
            return ToolExecutionResult.text((parameters.a + parameters.b) + "@" + context.getRunId());
        }
    }

    /**
     * 不含参数的失败测试工具。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    @ToolInfo(name = "fail", description = "主动失败")
    public static class FailingTool implements Tool<EmptyArgs> {
        /**
         * 返回空参数类型。
         *
         * @return 空参数类
         */
        @Override
        public Class<EmptyArgs> parameterType() {
            return EmptyArgs.class;
        }

        /**
         * 模拟工具执行失败。
         *
         * @param parameters 空参数
         * @param context 可信上下文
         * @return 不会返回
         */
        @Override
        public ToolExecutionResult execute(EmptyArgs parameters, ToolExecutionContext context) {
            throw new IllegalStateException("boom");
        }
    }

    /**
     * 无字段参数。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public static class EmptyArgs { }

    /**
     * 测试列表与枚举的参数。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public static class FilterArgs {
        /**
         * 待处理标签。
         */
        @ToolParam(description = "标签")
        public List<String> labels;
        /**
         * 执行模式。
         */
        @ToolParam(description = "模式")
        public Mode mode;
    }

    /**
     * 列表处理模式。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public enum Mode {
        /**
         * 快速模式。
         */
        FAST,
        /**
         * 慢速模式。
         */
        SLOW
    }

    /**
     * 列表和枚举测试工具。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    @ToolInfo(name = "filter", description = "按模式筛选标签")
    public static class FilterTool implements Tool<FilterArgs> {
        /**
         * 返回筛选参数类。
         *
         * @return 参数类
         */
        @Override
        public Class<FilterArgs> parameterType() { return FilterArgs.class; }

        /**
         * 返回测试输出。
         *
         * @param parameters 筛选参数
         * @param context 可信上下文
         * @return 文本输出
         */
        @Override
        public ToolExecutionResult execute(FilterArgs parameters, ToolExecutionContext context) {
            return ToolExecutionResult.text(parameters.mode.name());
        }
    }

    /**
     * 不支持的嵌套参数。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public static class NestedArgs {
        /**
         * 嵌套对象。
         */
        @ToolParam(description = "嵌套对象")
        public AddArgs nested;
    }

    /**
     * 显式描述工具的多类型参数。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public static class ExplicitArgs {
        /**
         * 标题。
         */
        @ToolParam(description = "标题")
        public String title;
        /**
         * 权重。
         */
        @ToolParam(description = "权重")
        public double weight;
        /**
         * 是否启用。
         */
        @ToolParam(description = "是否启用")
        public boolean enabled;
        /**
         * 整数位置列表。
         */
        @ToolParam(description = "位置")
        public List<Integer> offsets;
    }

    /**
     * 不依赖工具类注解的显式描述工具。
     *
     * @author Sebastian
     * @since 2026/10/02
     */
    public static class ExplicitTool implements Tool<ExplicitArgs> {
        /**
         * 返回参数类型。
         *
         * @return 参数类
         */
        @Override
        public Class<ExplicitArgs> parameterType() { return ExplicitArgs.class; }

        /**
         * 显式提供工具名称与说明。
         *
         * @return 工具描述
         */
        @Override
        public ToolDescriptor<ExplicitArgs> descriptor() {
            return ToolDescriptor.of("explicit", "验证多类型参数", ExplicitArgs.class);
        }

        /**
         * 返回接收到的标题和位置数量。
         *
         * @param parameters 多类型参数
         * @param context 可信上下文
         * @return 测试输出
         */
        @Override
        public ToolExecutionResult execute(ExplicitArgs parameters, ToolExecutionContext context) {
            return ToolExecutionResult.text(parameters.title + ":" + parameters.offsets.size());
        }
    }
}
