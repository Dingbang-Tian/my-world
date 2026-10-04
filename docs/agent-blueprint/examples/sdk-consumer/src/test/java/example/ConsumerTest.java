package example;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.api.AgentSdk;
import com.dingbang.myworld.agent.api.AgentService;
import com.dingbang.myworld.aiapp.codegen.api.CodegenSdk;
import com.dingbang.myworld.aiapp.codegen.api.CodegenService;
import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelGateway;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证独立工程以普通 Maven 依赖运行 Agent SDK。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
class ConsumerTest {
    /**
     * 独立消费者的临时工作目录。
     */
    @TempDir
    Path workspace;

    /**
     * 使用假模型完成与 codegen 演示相同的任务文本。
     */
    @Test
    void runsWithOrdinaryJarDependency() {
        // 不访问网络的单次模型入口。
        ModelGateway gateway = (request, listener) -> {
            // 模型最终回答。
            Message answer = new Message("answer", Role.ASSISTANT,
                    List.of(new TextContentBlock("目录检查完成")), List.of(), List.of(), Map.of());
            listener.onEvent(new TurnCompleted(new ModelTurn(answer, ModelFinishReason.STOP)));
            listener.onComplete();
        };
        // 可信应用定义。
        AgentDefinition definition = new AgentDefinition("sdk-demo", "codegen", "代码任务", "检查目录", "scripted", null);
        // 普通 JAR 提供的公开服务接口。
        AgentService service = AgentSdk.create(gateway, definition);
        // 独立消费者取得的最终结果。
        AgentResult result = service.run(new AgentRequest("owner", "sdk-demo", "codegen", null,
                "sdk-1", "检查目录", ModelOptions.empty()));
        assertEquals(AgentResultStatus.COMPLETED, result.getStatus());
        assertEquals("目录检查完成", result.getFinalText());
    }

    /**
     * 使用代码生成应用公开入口并直接调用同一 AgentService。
     */
    @Test
    void runsCodegenTaskThroughAgentService() {
        // 不依赖模型凭据的假模型入口。
        ModelGateway gateway = (request, listener) -> {
            // 模型最终回答。
            Message answer = new Message("codegen-answer", Role.ASSISTANT,
                    List.of(new TextContentBlock("目录检查完成")), List.of(), List.of(), Map.of());
            listener.onEvent(new TurnCompleted(new ModelTurn(answer, ModelFinishReason.STOP)));
            listener.onComplete();
        };
        // 只读代码生成应用。
        CodegenService codegen = CodegenSdk.readOnly(gateway, "scripted", workspace);
        // 与 Web 入口共用相同代码生成定义的公共服务。
        AgentService agent = codegen.agentService();
        // 使用相同任务文本的结果。
        AgentResult result = agent.run(new AgentRequest("owner", CodegenService.APP_ID,
                CodegenService.AGENT_ID, null, "codegen-sdk-1", "检查目录", ModelOptions.empty()));
        assertEquals(AgentResultStatus.COMPLETED, result.getStatus());
        assertEquals("目录检查完成", result.getFinalText());
    }
}
