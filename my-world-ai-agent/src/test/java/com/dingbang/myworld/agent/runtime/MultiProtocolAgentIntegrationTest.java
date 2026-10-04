package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.aiframework.protocol.MultiProtocolType;

import com.dingbang.myworld.agent.api.AgentDefinition;
import com.dingbang.myworld.agent.api.AgentRequest;
import com.dingbang.myworld.agent.api.AgentResultStatus;
import com.dingbang.myworld.agent.prompt.PromptTemplateRegistry;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.protocol.MultiProtocolGateway;
import com.dingbang.myworld.aiframework.protocol.MultiProtocolModelConfig;
import com.dingbang.myworld.aiframework.protocol.RoutedModelGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证相同 Agent 工具循环可由 Responses 和 Anthropic 模型驱动。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class MultiProtocolAgentIntegrationTest {
    /**
     * JSON 测试解析器。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 用两套本地协议事件完成同一个加法工具用例。
     *
     * @throws Exception 本地 HTTP fixture 失败时
     */
    @Test
    void bothProtocolsExecuteSameAgentToolLoop() throws Exception {
        // Responses 请求序列。
        List<JsonNode> responseRequests = new ArrayList<>();
        // Anthropic 请求序列。
        List<JsonNode> anthropicRequests = new ArrayList<>();
        // 本地协议服务。
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            responseRequests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            if (responseRequests.size() == 1) respond(exchange,
                    event("{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"function_call\",\"call_id\":\"r-tool\",\"name\":\"add\",\"arguments\":\"{\\\"a\\\":2,\\\"b\\\":3}\"}}")
                            + event("{\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"usage\":{\"input_tokens\":5,\"output_tokens\":2}}}"));
            else respond(exchange, event("{\"type\":\"response.output_text.delta\",\"delta\":\"5\"}")
                    + event("{\"type\":\"response.completed\",\"response\":{\"id\":\"r2\",\"usage\":{\"input_tokens\":8,\"output_tokens\":1}}}"));
        });
        server.createContext("/anthropic", exchange -> {
            anthropicRequests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            if (anthropicRequests.size() == 1) respond(exchange,
                    event("{\"type\":\"message_start\",\"message\":{\"id\":\"a1\"}}")
                            + event("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"a-tool\",\"name\":\"add\",\"input\":{\"a\":2,\"b\":3}}}")
                            + event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}")
                            + event("{\"type\":\"message_stop\"}"));
            else respond(exchange, event("{\"type\":\"message_start\",\"message\":{\"id\":\"a2\"}}")
                    + event("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"5\"}}")
                    + event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}")
                    + event("{\"type\":\"message_stop\"}"));
        });
        server.start();
        try {
            // 测试服务地址。
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            // 两个实际协议适配器。
            MultiProtocolGateway gateway = new MultiProtocolGateway(List.of(
                    new MultiProtocolModelConfig("responses", MultiProtocolType.RESPONSES,
                            URI.create(base + "/responses"), "wire-r", "key", ModelOptions.empty(), false, false, false),
                    new MultiProtocolModelConfig("anthropic", MultiProtocolType.ANTHROPIC,
                            URI.create(base + "/anthropic"), "wire-a", "key", ModelOptions.empty(), false, false, false)));
            // 可复用的加法 Java 工具。
            OpenAiChatAgentIntegrationTestAddTool tool = new OpenAiChatAgentIntegrationTestAddTool();
            // 同一个服务内的两个 Agent 定义，仅模型 ID 不同。
            DefaultAgentService service = new DefaultAgentService(new RoutedModelGateway(Map.of(
                    "responses", gateway, "anthropic", gateway)),
                    new PromptTemplateRegistry(new DefaultResourceLoader(), Map.of(), Map.of()),
                    List.of(definition("responses"), definition("anthropic")),
                    new ToolRegistry(List.of(tool)), List.of(), ForkJoinPool.commonPool());
            assertThat(service.run(new AgentRequest("app", "responses", null, "run-r", "计算 2+3"))
                    .getStatus()).isEqualTo(AgentResultStatus.COMPLETED);
            assertThat(service.run(new AgentRequest("app", "anthropic", null, "run-a", "计算 2+3"))
                    .getFinalText()).isEqualTo("5");
            assertThat(responseRequests).hasSize(2);
            assertThat(anthropicRequests).hasSize(2);
            assertThat(responseRequests.get(1).path("input").get(responseRequests.get(1).path("input").size() - 1)
                    .path("call_id").asText()).isEqualTo("r-tool");
            assertThat(anthropicRequests.get(1).path("messages").get(2).path("content").get(0)
                    .path("tool_use_id").asText()).isEqualTo("a-tool");
        } finally { server.stop(0); }
    }

    /**
     * 创建同样授权的 Agent 定义。
     *
     * @param modelId 本地模型与 Agent 标识
     * @return 可信定义
     */
    private static AgentDefinition definition(String modelId) {
        return new AgentDefinition("app", modelId, "数学助手", "计算", modelId,
                null, List.of("add"), List.of(), 4);
    }

    /**
     * 包装单个 SSE 数据事件。
     *
     * @param json JSON 事件
     * @return SSE 文本
     */
    private static String event(String json) { return "data: " + json + "\n\n"; }

    /**
     * 发送本地协议响应。
     *
     * @param exchange HTTP 交换
     * @param body SSE 响应文本
     * @throws IOException 输出失败时
     */
    private static void respond(HttpExchange exchange, String body) throws IOException {
        // UTF-8 响应字节。
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
