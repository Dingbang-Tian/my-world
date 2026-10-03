package com.dingbang.myworld.aiapp.codegen;

import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiapp.codegen.application.CodegenFactory;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证代码生成应用与公共 Agent 的完整工具回合和权限边界。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class CodegenServiceTest {
    /** 临时工作目录。 */
    @TempDir
    Path workspace;

    /**
     * 假模型读取目录后取得工具结果，且默认不暴露写工具。
     *
     * @throws Exception 文件创建失败时
     */
    @Test
    void readsWorkspaceThroughAgentWithoutWritePermission() throws Exception {
        Files.writeString(workspace.resolve("hello.txt"), "hello");
        /** 本地模型调用次数。 */
        AtomicInteger calls = new AtomicInteger();
        /** 每次模型请求的快照。 */
        List<ModelRequest> requests = Collections.synchronizedList(new ArrayList<>());
        /** 本地假模型。 */
        com.dingbang.myworld.aiframework.api.ModelGateway gateway = (request, listener) -> {
            requests.add(request);
            if (calls.incrementAndGet() == 1) {
                toolTurn(listener, "list_directory_tree", "{\"path\":\"\",\"depth\":1}");
            } else {
                textTurn(listener, "已发现 hello.txt");
            }
        };
        /** 含应用模板的仓库。 */
        PromptTemplateRegistry prompts = new PromptTemplateRegistry(new DefaultResourceLoader(),
                Collections.emptyMap(), Collections.emptyMap());
        /** 默认只读的应用服务。 */
        CodegenService service = new CodegenFactory().create(gateway, prompts, "scripted", workspace, false);
        /** 完成的应用结果。 */
        AgentResult result = service.run("owner", null, "request-1", "理解这个目录");
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(result.getFinalText()).contains("hello.txt");
        assertThat(calls.get()).isEqualTo(2);
        assertThat(requests.get(0).getTools()).hasSize(5);
        assertThat(requests.get(0).getTools()).noneMatch(tool -> tool.getName().equals("create_file"));
        assertThat(requests.get(0).getTools()).noneMatch(tool -> tool.getName().equals("execute_command"));
        assertThat(requests.get(1).getMessages()).anyMatch(message -> message.getRole() == Role.TOOL
                && message.getToolResults().get(0).getContent().contains("hello.txt"));
    }

    /**
     * 假模型伪造未授权写工具时，执行入口返回工具未找到且无副作用。
     */
    @Test
    void rejectsForgedWriteCall() {
        /** 本地模型调用次数。 */
        AtomicInteger calls = new AtomicInteger();
        /** 第二轮工具错误码。 */
        List<String> errors = new ArrayList<>();
        /** 伪造写工具的模型。 */
        com.dingbang.myworld.aiframework.api.ModelGateway gateway = (request, listener) -> {
            if (calls.incrementAndGet() == 1) {
                toolTurn(listener, "create_file", "{\"path\":\"bad.txt\",\"content\":\"bad\"}");
            } else {
                request.getMessages().stream().filter(message -> message.getRole() == Role.TOOL)
                        .forEach(message -> errors.add(message.getToolResults().get(0).getErrorCode()));
                textTurn(listener, "无法写入");
            }
        };
        /** 只读服务。 */
        CodegenService service = new CodegenFactory().create(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Collections.emptyMap(), Collections.emptyMap()),
                "scripted", workspace, false);
        assertThat(service.run("owner", null, "request-2", "创建文件").getStatus())
                .isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(errors).containsExactly("TOOL_NOT_FOUND");
        assertThat(Files.exists(workspace.resolve("bad.txt"))).isFalse();
    }

    /** 模型伪造未授权命令时，注册表拒绝执行且无命令报告。 */
    @Test
    void rejectsForgedCommandCall() {
        /** 模型调用次数。 */
        AtomicInteger calls = new AtomicInteger();
        /** 第二轮收到的工具错误码。 */
        List<String> errors = new ArrayList<>();
        /** 伪造命令调用的模型。 */
        com.dingbang.myworld.aiframework.api.ModelGateway gateway = (request, listener) -> {
            if (calls.incrementAndGet() == 1) {
                toolTurn(listener, "execute_command", "{\"command\":\"echo forbidden > forbidden.txt\"}");
            } else {
                request.getMessages().stream().filter(message -> message.getRole() == Role.TOOL)
                        .forEach(message -> errors.add(message.getToolResults().get(0).getErrorCode()));
                textTurn(listener, "命令未获授权");
            }
        };
        /** 只读代码生成服务。 */
        CodegenService service = new CodegenFactory().create(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Collections.emptyMap(), Collections.emptyMap()),
                "scripted", workspace, false);
        /** 运行结果。 */
        AgentResult result = service.run("owner", null, "request-command-off", "执行命令");
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(errors).containsExactly("TOOL_NOT_FOUND");
        assertThat(service.commandReports(result.getRunId())).isEmpty();
        assertThat(Files.exists(workspace.resolve("forbidden.txt"))).isFalse();
    }

    /**
     * 写能力显式启用后，工具结果和运行产物都记录新文件。
     *
     * @throws Exception 文件读取失败时
     */
    @Test
    void createsFileAndRecordsArtifactWhenEnabled() throws Exception {
        /** 模型调用次数。 */
        AtomicInteger calls = new AtomicInteger();
        /** 本地脚本模型。 */
        com.dingbang.myworld.aiframework.api.ModelGateway gateway = (request, listener) -> {
            if (calls.incrementAndGet() == 1) {
                assertThat(request.getTools()).hasSize(9);
                toolTurn(listener, "create_file", "{\"path\":\"result.txt\",\"content\":\"完成\"}");
            } else {
                assertThat(request.getMessages()).anyMatch(message -> message.getRole() == Role.TOOL
                        && message.getToolResults().get(0).getStatus() == ToolResultStatus.SUCCESS);
                textTurn(listener, "已创建 result.txt");
            }
        };
        /** 启用写文件的服务。 */
        CodegenService service = new CodegenFactory().create(gateway,
                new PromptTemplateRegistry(new DefaultResourceLoader(), Collections.emptyMap(), Collections.emptyMap()),
                "scripted", workspace, true);
        /** 完整结果。 */
        AgentResult result = service.run("owner", null, "request-3", "创建结果文件");
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
        assertThat(Files.readString(workspace.resolve("result.txt"))).isEqualTo("完成");
        assertThat(service.artifacts(result.getRunId())).hasSize(1);
        assertThat(service.artifacts(result.getRunId()).get(0).action()).isEqualTo("CREATE");
    }

    /**
     * 向监听器发送一次工具调用。
     *
     * @param listener 模型监听器
     * @param name 工具名称
     * @param arguments 参数 JSON
     */
    private static void toolTurn(ModelEventListener listener, String name, String arguments) {
        /** 工具调用消息。 */
        Message assistant = new Message("assistant-tool", Role.ASSISTANT, List.of(),
                List.of(new ToolCall("call-1", name, arguments)), List.of(), Collections.emptyMap());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.TOOL_CALLS)));
        listener.onComplete();
    }

    /**
     * 向监听器发送最终文本。
     *
     * @param listener 模型监听器
     * @param answer 最终答案
     */
    private static void textTurn(ModelEventListener listener, String answer) {
        /** 助手最终消息。 */
        Message assistant = new Message("assistant-final", Role.ASSISTANT,
                List.of(new TextContentBlock(answer)), List.of(), List.of(), Collections.emptyMap());
        listener.onEvent(new TurnCompleted(new ModelTurn(assistant, ModelFinishReason.STOP)));
        listener.onComplete();
    }
}
