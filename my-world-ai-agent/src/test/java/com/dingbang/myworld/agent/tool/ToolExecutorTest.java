package com.dingbang.myworld.agent.tool;

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
        ToolExecutorTestAddTool tool = new ToolExecutorTestAddTool();
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
        ToolExecutorTestAddTool tool = new ToolExecutorTestAddTool();
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
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(new ToolExecutorTestAddTool()));
        // 空授权快照上的执行器。
        ToolExecutor executor = new ToolExecutor(registry.select(Collections.emptyList()));
        // 未获授权的结果。
        ToolResult result = executor.execute(new ToolCall("call", "add", "{\"a\":2,\"b\":3}"),
                new ToolExecutionContext("run", "session", null, null), null);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_NOT_FOUND");
        assertThatThrownBy(() -> new ToolRegistry(Arrays.asList(new ToolExecutorTestAddTool(), new ToolExecutorTestAddTool())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("工具名称冲突");
    }

    /**
     * Java 工具异常与监听器异常不改变主结果。
     */
    @Test
    void isolatesListenerFailureAndReportsToolFailure() {
        // 抛错工具的执行器。
        ToolExecutor executor = new ToolExecutor(new ToolRegistry(Collections.singletonList(new ToolExecutorTestFailingTool())));
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
        ToolDescriptor<ToolExecutorTestFilterArgs> descriptor = new ToolExecutorTestFilterTool().descriptor();
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
        assertThatThrownBy(() -> ToolDescriptor.of("nested", "嵌套参数", ToolExecutorTestNestedArgs.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持");
    }

    /**
     * 显式描述入口与字符串、浮点、布尔、整数列表采用相同校验规则。
     */
    @Test
    void supportsExplicitDescriptorAndScalarTypes() {
        // 无工具类注解的显式描述工具。
        ToolExecutorTestExplicitTool tool = new ToolExecutorTestExplicitTool();
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










}
