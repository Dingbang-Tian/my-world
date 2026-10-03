package com.dingbang.myworld.aiapp.codegen;

import com.dingbang.myworld.agent.api.AgentEvent;
import com.dingbang.myworld.agent.api.AgentEventListener;
import com.dingbang.myworld.agent.api.AgentEventType;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentRun;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiapp.codegen.application.CodegenFactory;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用确定性模型验证代码生成、编译失败、修改和重新运行的完整链路。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class CodegenCommandLoopTest {
    /** 代码生成的临时目录。 */
    @TempDir
    Path workspace;
    /** 测试 JSON 编码器。 */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 验证脚本模型收到真实编译错误后修正 Java 文件并通过验证。
     *
     * @throws Exception 文件或异步运行失败时
     */
    @Test
    void generatesCompilesCorrectsAndReports() throws Exception {
        /** 当前 JDK 的编译与运行命令。 */
        String javaHome = System.getProperty("java.home");
        /** 模型请求的顺序号。 */
        AtomicInteger turn = new AtomicInteger();
        /** 收集到的工具结果轨迹。 */
        List<ToolResult> results = Collections.synchronizedList(new ArrayList<>());
        /** 确定性假模型。 */
        com.dingbang.myworld.aiframework.api.ModelGateway gateway = (request, listener) -> {
            /** 当前回合编号。 */
            int current = turn.incrementAndGet();
            if (current > 1) {
                results.add(lastToolResult(request));
            }
            switch (current) {
                case 1 -> toolTurn(listener, current, "create_file", Map.of("path", "HelloAgent.java",
                        "content", "public class HelloAgent { public static void main(String[] args) { System.out.println(\"HelloAgent\"); }\n"));
                case 2 -> toolTurn(listener, current, "execute_command", Map.of("command",
                        "\"" + javaHome + "/bin/javac\" HelloAgent.java && \"" + javaHome + "/bin/java\" HelloAgent"));
                case 3 -> {
                    assertThat(results.get(1).getContent()).contains("exitCode=").doesNotContain("exitCode=0");
                    toolTurn(listener, current, "view_file", Map.of("path", "HelloAgent.java"));
                }
                case 4 -> {
                    /** 从真实文件读取结果获得的版本。 */
                    String hash = results.get(2).getContent().substring(7, 71);
                    toolTurn(listener, current, "edit_file", Map.of("path", "HelloAgent.java",
                            "expectedHash", hash, "mode", "append", "content", "}\n"));
                }
                case 5 -> toolTurn(listener, current, "execute_command", Map.of("command",
                        "\"" + javaHome + "/bin/javac\" HelloAgent.java && \"" + javaHome + "/bin/java\" HelloAgent"));
                case 6 -> {
                    assertThat(results.get(4).getContent()).contains("exitCode=0", "HelloAgent");
                    textTurn(listener, "HelloAgent.java 已修正，编译和运行通过");
                }
                default -> throw new AssertionError("意外模型回合: " + current);
            }
        };
        /** 配置开启写文件和命令的代码生成服务。 */
        CodegenService service = new CodegenFactory().create(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                "scripted", workspace, true, true, List.of("PATH", "JAVA_HOME"));
        /** 可观察的运行句柄。 */
        AgentRun run = service.prepare("owner", null, "s12-request", "生成并验证 HelloAgent.java");
        /** 运行事件轨迹。 */
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        run.subscribe(new AgentEventListener() {
            /** {@inheritDoc} */
            @Override
            public void onEvent(AgentEvent event) {
                events.add(event);
            }

            /** {@inheritDoc} */
            @Override
            public void onComplete() {
            }
        });
        run.execute();
        /** 最终结果。 */
        AgentResult result = run.getResult().toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(turn.get()).isEqualTo(6);
        assertThat(results).hasSize(5).allMatch(item -> item.getStatus() == ToolResultStatus.SUCCESS);
        assertThat(results.get(1).getContent()).contains("exitCode=1");
        assertThat(results.get(4).getContent()).contains("timedOut=false", "exitCode=0", "HelloAgent");
        assertThat(Files.readString(workspace.resolve("HelloAgent.java"))).endsWith("}\n");
        assertThat(Files.exists(workspace.resolve("HelloAgent.class"))).isTrue();
        assertThat(service.artifacts(result.getRunId())).extracting("action").containsExactly("CREATE", "EDIT");
        assertThat(service.commandReports(result.getRunId())).hasSize(2);
        assertThat(service.commandReports(result.getRunId()).get(0).exitCode()).isEqualTo(1);
        assertThat(service.commandReports(result.getRunId()).get(1).output()).contains("HelloAgent");
        assertThat(events).anyMatch(event -> event.getType() == AgentEventType.TOOL_EXECUTION);
    }

    /**
     * 验证计划中的真实编译失败驱动修正步骤，且整体状态保留失败记录。
     *
     * @throws Exception 文件或异步运行失败时
     */
    @Test
    void planCarriesCompileFailureIntoCorrectionStep() throws Exception {
        /** 当前 JDK 的编译与运行命令。 */
        String javaHome = System.getProperty("java.home");
        /** 模型请求序号。 */
        AtomicInteger turn = new AtomicInteger();
        /** 本地确定性计划模型。 */
        com.dingbang.myworld.aiframework.api.ModelGateway gateway = (request, listener) -> {
            /** 当前全局模型回合。 */
            int current = turn.incrementAndGet();
            switch (current) {
                case 1 -> toolJson(listener, current, "create_plan", json.valueToTree(Map.of(
                        "name", "生成编译修正", "description", "创建 HelloAgent.java 并验证",
                        "steps", List.of("生成源文件", "编译源文件", "根据编译错误修正并重新验证"),
                        "failurePolicy", "CONTINUE")).toString());
                case 2 -> toolTurn(listener, current, "create_file", Map.of("path", "HelloAgent.java",
                        "content", "public class HelloAgent { public static void main(String[] args) { System.out.println(\"HelloAgent\"); }\n"));
                case 3 -> textTurn(listener, "已生成源文件");
                case 4 -> toolTurn(listener, current, "execute_command", Map.of("command",
                        "\"" + javaHome + "/bin/javac\" HelloAgent.java"));
                case 5 -> {
                    assertThat(lastToolResult(request).getContent()).contains("exitCode=1");
                    textTurn(listener, "编译失败，需要修正");
                }
                case 6 -> {
                    assertThat(request.getMessages().stream().map(message -> message.getContentBlocks().isEmpty()
                                    ? "" : ((TextContentBlock) message.getContentBlocks().get(0)).getText()).toList())
                            .anyMatch(value -> value.contains("步骤 2 [FAILED]") && value.contains("exitCode=1"));
                    toolTurn(listener, current, "view_file", Map.of("path", "HelloAgent.java"));
                }
                case 7 -> {
                    /** 真实 view_file 返回的内容版本。 */
                    String hash = lastToolResult(request).getContent().substring(7, 71);
                    toolTurn(listener, current, "edit_file", Map.of("path", "HelloAgent.java",
                            "expectedHash", hash, "mode", "append", "content", "}\n"));
                }
                case 8 -> toolTurn(listener, current, "execute_command", Map.of("command",
                        "\"" + javaHome + "/bin/javac\" HelloAgent.java && \"" + javaHome
                                + "/bin/java\" HelloAgent"));
                case 9 -> {
                    assertThat(lastToolResult(request).getContent()).contains("exitCode=0", "HelloAgent");
                    textTurn(listener, "已修正并通过编译运行");
                }
                case 10 -> {
                    assertThat(lastToolResult(request).getErrorCode()).isEqualTo("PLAN_FAILED");
                    textTurn(listener, "最终文件已验证，但计划中曾有编译失败");
                }
                default -> throw new AssertionError("多余模型回合");
            }
        };
        /** 同时授权文件、命令与计划的代码生成服务。 */
        CodegenService service = new CodegenFactory().create(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                "scripted", workspace, true, true, List.of("PATH", "JAVA_HOME", "LANG", "TMPDIR"), true);
        /** 真实计划运行结果。 */
        AgentResult result = service.run("owner", null, "s13-codegen", "生成、编译并修正 Java 文件");
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
        assertThat(result.getError().getCode()).isEqualTo("PLAN_FAILED");
        assertThat(result.getError().getMessage()).contains("步骤 2 [FAILED]", "步骤 3 [SUCCEEDED]");
        assertThat(turn.get()).isEqualTo(10);
        assertThat(Files.readString(workspace.resolve("HelloAgent.java"))).endsWith("}\n");
        assertThat(Files.exists(workspace.resolve("HelloAgent.class"))).isTrue();
        assertThat(service.commandReports(result.getRunId())).extracting("exitCode")
                .containsExactly(1, 0);
        assertThat(service.artifacts(result.getRunId())).extracting("action")
                .containsExactly("CREATE", "EDIT");
        System.out.println("S13 codegen trace: create_plan -> create_file -> javac=1 [FAILED]"
                + " -> view/edit -> javac+java=0 [SUCCEEDED] -> plan FAILED");
    }

    /**
     * 发送带原始 JSON 参数的计划工具模型回合。
     *
     * @param listener 模型监听器
     * @param turn 全局模型回合
     * @param name 工具名称
     * @param arguments JSON 参数
     */
    private void toolJson(ModelEventListener listener, int turn, String name, String arguments) {
        /** 完整助手工具消息。 */
        Message assistant = new Message("assistant-" + turn, Role.ASSISTANT, List.of(),
                List.of(new ToolCall("call-" + turn, name, arguments)), List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new com.dingbang.myworld.aiframework.api.ModelTurn(
                assistant, ModelFinishReason.TOOL_CALLS)));
        listener.onComplete();
    }

    /**
     * 从模型请求中取得上一次工具结果。
     *
     * @param request 当前模型请求
     * @return 上次工具结果
     */
    private ToolResult lastToolResult(ModelRequest request) {
        return request.getMessages().stream().filter(message -> message.getRole() == Role.TOOL)
                .reduce((first, second) -> second).orElseThrow().getToolResults().get(0);
    }

    /**
     * 发送一次模型工具回合。
     *
     * @param listener 模型事件监听器
     * @param turn 当前回合
     * @param name 工具名
     * @param args 工具参数
     */
    private void toolTurn(ModelEventListener listener, int turn, String name, Map<String, String> args) {
        /** 助手的工具调用消息。 */
        Message assistant = new Message("assistant-" + turn, Role.ASSISTANT, List.of(),
                List.of(new ToolCall("call-" + turn, name, json.valueToTree(args).toString())),
                List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new com.dingbang.myworld.aiframework.api.ModelTurn(
                assistant, ModelFinishReason.TOOL_CALLS)));
        listener.onComplete();
    }

    /**
     * 发送最终文本。
     *
     * @param listener 模型事件监听器
     * @param text 结果文本
     */
    private void textTurn(ModelEventListener listener, String text) {
        /** 助手最终消息。 */
        Message assistant = new Message("assistant-final", Role.ASSISTANT,
                List.of(new TextContentBlock(text)), List.of(), List.of(), Map.of());
        listener.onEvent(new TurnCompleted(new com.dingbang.myworld.aiframework.api.ModelTurn(
                assistant, ModelFinishReason.STOP)));
        listener.onComplete();
    }
}
