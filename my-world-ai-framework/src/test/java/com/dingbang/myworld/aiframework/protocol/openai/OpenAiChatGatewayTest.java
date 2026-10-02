package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.ModelToolDefinition;
import com.dingbang.myworld.aiframework.api.ModelTurn;
import com.dingbang.myworld.aiframework.api.event.ModelEvent;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.api.event.ReasoningDelta;
import com.dingbang.myworld.aiframework.api.event.TextDelta;
import com.dingbang.myworld.aiframework.api.event.TurnCompleted;
import com.dingbang.myworld.aiframework.api.event.UsageReported;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用本地 HTTP 服务验证 Chat 协议编码、流聚合和失败边界。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class OpenAiChatGatewayTest {

    /** JSON 测试解析器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 验证多索引工具分片、推理、用量尾块及第二轮历史转换。
     *
     * @throws Exception 本地服务或 JSON 解析失败时
     */
    @Test
    void streamsToolsUsageAndHistory() throws Exception {
        /** 收到的 HTTP 请求体。 */
        List<JsonNode> requests = new ArrayList<>();
        /** 本地请求计数。 */
        AtomicInteger count = new AtomicInteger();
        /** 只在本机监听的测试服务。 */
        HttpServer server = server();
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            if (count.incrementAndGet() == 1) {
                respond(exchange, 200, "data: {\"id\":\"c1\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"想\",\"tool_calls\":[{\"index\":1,\"id\":\"b\",\"function\":{\"name\":\"mu\",\"arguments\":\"{\\\"x\\\":\"}},{\"index\":0,\"id\":\"a\",\"function\":{\"name\":\"ad\",\"arguments\":\"{\\\"x\\\":\"}}]},\"finish_reason\":null}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"好\",\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"d\",\"arguments\":\"2}\"}},{\"index\":1,\"function\":{\"name\":\"l\",\"arguments\":\"3}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5,\"total_tokens\":17,\"completion_tokens_details\":{\"reasoning_tokens\":2}}}\n\n"
                        + "data: [DONE]\n\n");
            } else {
                respond(exchange, 200, "data: {\"id\":\"c2\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"结果\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n");
            }
        });
        server.start();
        try {
            /** 本地配置的模型网关。 */
            OpenAiChatGateway gateway = gateway(server);
            /** 首轮监听器。 */
            Recorder first = new Recorder();
            gateway.generate(new ModelRequest("local", Collections.singletonList(textMessage("u", Role.USER, "计算")),
                    Collections.singletonList(new ModelToolDefinition("add", "加法", "{\"type\":\"object\"}")),
                    new ModelOptions(0.2, null, "low")), first);
            /** 首轮完整结果。 */
            ModelTurn turn = first.turn();
            assertThat(first.error).isNull();
            assertThat(first.completed).isEqualTo(1);
            assertThat(first.events).extracting(ModelEvent::getClass)
                    .containsExactly(ReasoningDelta.class, ReasoningDelta.class,
                            UsageReported.class, TurnCompleted.class);
            assertThat(turn.getFinishReason()).isEqualTo(ModelFinishReason.TOOL_CALLS);
            assertThat(turn.getAssistantMessage().getToolCalls()).extracting(ToolCall::getName)
                    .containsExactly("add", "mul");
            assertThat(turn.getAssistantMessage().getToolCalls()).extracting(ToolCall::getArgumentsJson)
                    .containsExactly("{\"x\":2}", "{\"x\":3}");
            assertThat(turn.getAssistantMessage().getProviderMetadata()).containsEntry("reasoning_content", "想好");
            assertThat(turn.getUsage().getTotalTokens()).isEqualTo(17);
            assertThat(turn.getUsage().getReasoningTokens()).isEqualTo(2);
            assertThat(requests.get(0).path("model").asText()).isEqualTo("wire-model");
            assertThat(requests.get(0).path("messages").get(0).path("role").asText()).isEqualTo("user");
            assertThat(requests.get(0).path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(requests.get(0).path("max_completion_tokens").asInt()).isEqualTo(100);
            assertThat(requests.get(0).path("reasoning_effort").asText()).isEqualTo("low");
            assertThat(requests.get(0).path("tools").get(0).path("function").path("parameters").isObject()).isTrue();

            /** 首轮工具结果消息。 */
            Message toolResult = new Message("t", Role.TOOL, Collections.emptyList(), Collections.emptyList(),
                    Collections.singletonList(new ToolResult("a", ToolResultStatus.SUCCESS, "5", null, false)),
                    Collections.emptyMap());
            /** 第二轮监听器。 */
            Recorder second = new Recorder();
            gateway.generate(new ModelRequest("local", Arrays.asList(textMessage("u", Role.USER, "计算"),
                    turn.getAssistantMessage(), toolResult)), second);
            assertThat(second.error).isNull();
            assertThat(second.turn().getFinishReason()).isEqualTo(ModelFinishReason.STOP);
            assertThat(second.events).extracting(ModelEvent::getClass)
                    .containsExactly(TextDelta.class, TurnCompleted.class);
            assertThat(requests.get(1).path("temperature").asDouble()).isEqualTo(0.8);
            assertThat(requests.get(1).path("messages").get(1).path("tool_calls").get(0).path("id").asText())
                    .isEqualTo("a");
            assertThat(requests.get(1).path("messages").get(1).path("reasoning_content").asText())
                    .isEqualTo("想好");
            assertThat(requests.get(1).path("messages").get(2).path("tool_call_id").asText())
                    .isEqualTo("a");
            assertThat(requests.get(1).path("messages").get(2).path("content").asText())
                    .contains("SUCCESS", "5");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证 HTTP 错误、断流和未知结束原因不会产生正常完成事件。
     *
     * @throws Exception 本地服务启动失败时
     */
    @Test
    void reportsHttpErrorsAndBrokenStreams() throws Exception {
        /** 本地测试服务。 */
        HttpServer server = server();
        /** 下一次响应使用的状态码。 */
        AtomicInteger status = new AtomicInteger(401);
        server.createContext("/v1/chat/completions", exchange -> {
            /** 当前响应状态。 */
            int code = status.get();
            if (code != 200) {
                respond(exchange, code, "private error body");
            } else {
                respond(exchange, 200, "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"部分\"},\"finish_reason\":\"stop\"}]}\n\n");
            }
        });
        server.start();
        try {
            /** 被测模型网关。 */
            OpenAiChatGateway gateway = gateway(server);
            /** 当前待测试的 HTTP 错误状态码。 */
            for (int code : new int[]{401, 429, 500}) {
                status.set(code);
                /** 当前错误响应的监听器。 */
                Recorder recorder = new Recorder();
                gateway.generate(request(), recorder);
                assertThat(recorder.error).isInstanceOf(OpenAiChatProtocolException.class)
                        .hasMessageContaining(Integer.toString(code)).hasMessageNotContaining("private error body");
                assertThat(((OpenAiChatProtocolException) recorder.error).getHttpStatus()).isEqualTo(code);
                assertThat(recorder.completed).isZero();
            }
            status.set(200);
            /** 已给出 finish_reason 却缺少 DONE 的响应。 */
            Recorder broken = new Recorder();
            gateway.generate(request(), broken);
            assertThat(broken.error).hasMessageContaining("[DONE]");
            assertThat(broken.events).extracting(ModelEvent::getClass).containsExactly(TextDelta.class);
            assertThat(broken.completed).isZero();
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证未知结束原因保留为 UNKNOWN，供 Agent 判定失败。
     *
     * @throws Exception 本地服务启动失败时
     */
    @Test
    void retainsUnknownFinishReason() throws Exception {
        /** 本地测试服务。 */
        HttpServer server = server();
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200,
                "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"new_reason\"}]}\n\n"
                        + "data: [DONE]\n\n"));
        server.start();
        try {
            /** 完整回合记录器。 */
            Recorder recorder = new Recorder();
            gateway(server).generate(request(), recorder);
            assertThat(recorder.error).isNull();
            assertThat(recorder.turn().getFinishReason()).isEqualTo(ModelFinishReason.UNKNOWN);
        } finally {
            server.stop(0);
        }
    }

    /**
     * 验证两个 modelId 选择各自的服务端模型和默认值，调用级覆盖不污染后续请求。
     *
     * @throws Exception 本地服务或请求处理失败时
     */
    @Test
    void isolatesRegisteredModelsAndCallOptions() throws Exception {
        /** 按发送顺序保存的 HTTP 请求。 */
        List<JsonNode> requests = new ArrayList<>();
        /** 本地测试服务。 */
        HttpServer server = server();
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n");
        });
        server.start();
        try {
            /** 两个模型共用的本地接口地址。 */
            java.net.URI endpoint = java.net.URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/v1/chat/completions");
            /** 已注册两个独立实例的网关。 */
            OpenAiChatGateway gateway = new OpenAiChatGateway(Arrays.asList(
                    new OpenAiChatModelConfig("fast", "fixture", endpoint, "wire-fast", "key-fast",
                            new ModelOptions(0.1, 20, null)),
                    new OpenAiChatModelConfig("deep", "fixture", endpoint, "wire-deep", "key-deep",
                            new ModelOptions(0.9, 200, null))));
            gateway.generate(new ModelRequest("fast", request().getMessages(), Collections.emptyList(),
                    new ModelOptions(0.4, null, null)), new Recorder());
            gateway.generate(new ModelRequest("deep", request().getMessages()), new Recorder());
            gateway.generate(new ModelRequest("fast", request().getMessages()), new Recorder());
            assertThat(requests).hasSize(3);
            assertThat(requests).extracting(node -> node.path("model").asText())
                    .containsExactly("wire-fast", "wire-deep", "wire-fast");
            assertThat(requests).extracting(node -> node.path("temperature").asDouble())
                    .containsExactly(0.4, 0.9, 0.1);
            assertThat(requests).extracting(node -> node.path("max_completion_tokens").asInt())
                    .containsExactly(20, 200, 20);
        } finally {
            server.stop(0);
        }
    }

    /**
     * 创建本机动态端口的测试服务。
     *
     * @return 尚未启动的 HTTP 服务
     * @throws IOException 端口绑定失败时
     */
    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    /**
     * 创建指向测试服务的模型网关。
     *
     * @param server 本地服务
     * @return 已注册的模型网关
     */
    private static OpenAiChatGateway gateway(HttpServer server) {
        return new OpenAiChatGateway(Collections.singletonList(new OpenAiChatModelConfig("local", "fixture",
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions"),
                "wire-model", "test-only-key", new ModelOptions(0.8, 100, null), true)));
    }

    /**
     * 创建最小无工具请求。
     *
     * @return 模型请求
     */
    private static ModelRequest request() {
        return new ModelRequest("local", Collections.singletonList(textMessage("u", Role.USER, "你好")));
    }

    /**
     * 创建文本消息。
     *
     * @param id 消息标识
     * @param role 角色
     * @param text 文本
     * @return 中立消息
     */
    private static Message textMessage(String id, Role role, String text) {
        return new Message(id, role, Collections.singletonList(new TextContentBlock(text)),
                Collections.emptyList(), Collections.emptyList(), Map.of());
    }

    /**
     * 返回指定 HTTP 状态与响应体。
     *
     * @param exchange 当前 HTTP 交换
     * @param status HTTP 状态码
     * @param body 响应内容
     * @throws IOException 写响应失败时
     */
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        /** UTF-8 响应内容。 */
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        /** 当前响应体的输出流。 */
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /**
     * 记录单次模型调用中的事件与终态。
     */
    private static final class Recorder implements ModelEventListener {
        /** 按顺序收到的模型事件。 */
        private final List<ModelEvent> events = new ArrayList<>();
        /** 模型失败原因。 */
        private Throwable error;
        /** 正常完成次数。 */
        private int completed;

        /**
         * 保存模型事件。
         *
         * @param event 当前模型事件
         */
        @Override
        public void onEvent(ModelEvent event) {
            events.add(event);
        }

        /**
         * 保存模型错误。
         *
         * @param error 失败原因
         */
        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        /**
         * 记录正常结束通知。
         */
        @Override
        public void onComplete() {
            completed++;
        }

        /**
         * 读取唯一完整模型回合。
         *
         * @return 完整回合
         */
        private ModelTurn turn() {
            return ((TurnCompleted) events.get(events.size() - 1)).getTurn();
        }
    }
}
