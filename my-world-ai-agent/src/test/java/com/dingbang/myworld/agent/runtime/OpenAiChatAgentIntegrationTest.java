package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResult;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.agent.tool.annotation.ToolInfo;
import com.dingbang.myworld.agent.tool.annotation.ToolParam;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatGateway;
import com.dingbang.myworld.aiframework.protocol.openai.OpenAiChatModelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证真实 Chat HTTP 适配器与现有唯一 Agent 工具循环协作。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class OpenAiChatAgentIntegrationTest {

    /**
     * 验证非法参数经模型纠正后只执行一次工具且每轮只有一次 HTTP 请求。
     *
     * @throws Exception 本地服务或运行失败时
     */
    @Test
    void executesCorrectedToolCallThroughHttp() throws Exception {
        /** 监听本机动态端口的测试服务。 */
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        /** 收到的模型请求体。 */
        List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
        /** 测试 JSON 解析器。 */
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            /** 当前模型回合编号。 */
            int round = requests.size();
            if (round == 1) {
                respond(exchange, "data: {\"id\":\"r1\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"bad\",\"type\":\"function\",\"function\":{\"name\":\"add\",\"arguments\":\"{\\\"a\\\":2}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                        + "data: [DONE]\n\n");
            } else if (round == 2) {
                respond(exchange, "data: {\"id\":\"r2\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"fixed\",\"type\":\"function\",\"function\":{\"name\":\"add\",\"arguments\":\"{\\\"a\\\":2,\\\"b\\\":3}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                        + "data: [DONE]\n\n");
            } else {
                respond(exchange, "data: {\"id\":\"r3\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"5\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":1,\"total_tokens\":10}}\n\n"
                        + "data: [DONE]\n\n");
            }
        });
        server.start();
        try {
            /** 真实协议的单次模型网关。 */
            OpenAiChatGateway gateway = new OpenAiChatGateway(Collections.singletonList(
                    new OpenAiChatModelConfig("local", "fixture", URI.create("http://127.0.0.1:"
                            + server.getAddress().getPort() + "/v1/chat/completions"),
                            "wire-model", "test-key", ModelOptions.empty())));
            /** 记录真实 Java 调用次数的工具。 */
            AddTool tool = new AddTool();
            /** 固定系统模板和工具授权的 Agent 服务。 */
            DefaultAgentService service = new DefaultAgentService(gateway,
                    new PromptTemplateRegistry(new DefaultResourceLoader(), Collections.emptyMap(),
                            Collections.emptyMap()),
                    Collections.singletonList(new AgentDefinition("app", "math", "数学助手", "计算", "local",
                            null, Collections.singletonList("add"), Collections.emptyList(), 4)),
                    new ToolRegistry(Collections.singletonList(tool)), Collections.emptyList(),
                    ForkJoinPool.commonPool());
            /** 完整 Agent 结果。 */
            AgentResult result = service.run(new AgentRequest("app", "math", null, "s07", "计算 2+3"));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(result.getFinalText()).isEqualTo("5");
            assertThat(result.getUsage().getTotalTokens()).isEqualTo(10);
            assertThat(requests).hasSize(3);
            assertThat(tool.invocations.get()).isEqualTo(1);
            assertThat(requests.get(0).path("tools").get(0).path("function").path("name").asText())
                    .isEqualTo("add");
            assertThat(requests.get(1).path("messages").get(3).path("tool_call_id").asText())
                    .isEqualTo("bad");
            assertThat(requests.get(1).path("messages").get(3).path("content").asText())
                    .contains("TOOL_VALIDATION_ERROR");
            assertThat(requests.get(2).path("messages").get(5).path("tool_call_id").asText())
                    .isEqualTo("fixed");
            assertThat(requests.get(2).path("messages").get(5).path("content").asText())
                    .contains("5");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 向本地客户端返回 UTF-8 SSE 内容。
     *
     * @param exchange 当前 HTTP 交换
     * @param body 完整 SSE 响应
     * @throws IOException 写响应失败时
     */
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        /** UTF-8 响应字节。 */
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        /** 当前响应体的输出流。 */
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /**
     * 测试用加法参数。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    public static final class AddArgs {
        /** 第一个加数。 */
        @ToolParam(description = "第一个加数")
        public int a;
        /** 第二个加数。 */
        @ToolParam(description = "第二个加数")
        public int b;
    }

    /**
     * 带执行计数的测试加法工具。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    @ToolInfo(name = "add", description = "计算两个整数之和")
    public static final class AddTool implements Tool<AddArgs> {
        /** 已执行的 Java 工具次数。 */
        private final AtomicInteger invocations = new AtomicInteger();

        /**
         * 返回参数类型。
         *
         * @return 加法参数类型
         */
        @Override
        public Class<AddArgs> parameterType() {
            return AddArgs.class;
        }

        /**
         * 执行整数加法并计数。
         *
         * @param parameters 已校验的工具参数
         * @param context 可信运行上下文
         * @return 整数求和结果
         */
        @Override
        public ToolExecutionResult execute(AddArgs parameters, ToolExecutionContext context) {
            invocations.incrementAndGet();
            return ToolExecutionResult.text(Integer.toString(parameters.a + parameters.b));
        }
    }
}
