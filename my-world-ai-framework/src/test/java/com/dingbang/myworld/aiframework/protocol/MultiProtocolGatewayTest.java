package com.dingbang.myworld.aiframework.protocol;

import com.dingbang.myworld.aiframework.api.ModelFinishReason;
import com.dingbang.myworld.aiframework.api.CancellationToken;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
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
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import com.dingbang.myworld.aiframework.model.content.MediaContentBlock;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用本地协议流验证 Responses 与 Anthropic 的同一模型回合语义。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class MultiProtocolGatewayTest {
    /** 测试 JSON 解析器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 验证显式启用的 MiMo 兼容配置才回传无签名推理历史。
     *
     * @throws Exception 本地 HTTP 服务失败时
     */
    @Test
    void unsignedThinkingHistoryIsExplicitCapability() throws Exception {
        /** 收到的请求体。 */
        List<JsonNode> requests = new ArrayList<>();
        /** 本地 fixture 服务。 */
        HttpServer server = server();
        server.createContext("/anthropic", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, event("{\"type\":\"message_start\",\"message\":{\"id\":\"m\"}}")
                    + event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}")
                    + event("{\"type\":\"message_stop\"}"));
        });
        server.start();
        try {
            /** 带旧推理但没有签名的助手消息。 */
            Message oldAssistant = new Message("a", Role.ASSISTANT, List.of(new TextContentBlock("以前")),
                    List.of(), List.of(), Map.of("thinking", "legacy"));
            /** 两个显式不同的模型实例。 */
            MultiProtocolGateway gateway = new MultiProtocolGateway(List.of(
                    config(server, "anthropic", MultiProtocolModelConfig.Protocol.ANTHROPIC, false, true, true),
                    new MultiProtocolModelConfig("strict", MultiProtocolModelConfig.Protocol.ANTHROPIC,
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/anthropic"),
                            "wire", "test-key", ModelOptions.empty(), false, true, false)));
            /** 历史对话。 */
            List<Message> history = List.of(text("u1", Role.USER, "旧问题"), oldAssistant,
                    text("u2", Role.USER, "继续"));
            gateway.generate(new ModelRequest("anthropic", history), new Recorder());
            gateway.generate(new ModelRequest("strict", history), new Recorder());
            assertThat(requests.get(0).path("messages").get(1).path("content").get(0)
                    .path("thinking").asText()).isEqualTo("legacy");
            assertThat(requests.get(1).path("messages").get(1).path("content").get(0)
                    .path("type").asText()).isEqualTo("text");
        } finally { server.stop(0); }
    }

    /**
     * 验证取消后的网络断开以取消错误结束，不发完整回合。
     *
     * @throws Exception 本地流与异步等待失败时
     */
    @Test
    void cancellationOverridesLateStreamFailure() throws Exception {
        /** 首段内容发送信号。 */
        CountDownLatch sent = new CountDownLatch(1);
        /** 服务端释放信号。 */
        CountDownLatch release = new CountDownLatch(1);
        /** 本地 fixture 服务。 */
        HttpServer server = server();
        server.createContext("/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(event("{\"type\":\"response.output_text.delta\",\"delta\":\"a\"}")
                        .getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                sent.countDown();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        /** 单次调用的工作线程。 */
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            /** 与调用共享的取消信号。 */
            CancellationToken cancellation = new CancellationToken();
            /** 流回调记录。 */
            Recorder recorder = new Recorder();
            /** 带执行边界的模型请求。 */
            ModelRequest request = new ModelRequest("responses", List.of(text("u", Role.USER, "hi")),
                    List.of(), ModelOptions.empty(),
                    new ModelExecutionContext(Instant.now().plusSeconds(5), cancellation, 100));
            /** 当前流式调用。 */
            Future<?> active = executor.submit(() -> new MultiProtocolGateway(List.of(config(server,
                    "responses", MultiProtocolModelConfig.Protocol.RESPONSES, false, false, false)))
                    .generate(request, recorder));
            assertThat(sent.await(2, TimeUnit.SECONDS)).isTrue();
            cancellation.cancel();
            active.get(3, TimeUnit.SECONDS);
            assertThat(recorder.error).isInstanceOf(ExecutionControlException.class);
            assertThat(((ExecutionControlException) recorder.error).getCode()).isEqualTo("CANCELLED");
            assertThat(recorder.completed).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            server.stop(0);
        }
    }

    /**
     * 验证 Responses 工具调用、推理、图片与工具结果配对。
     *
     * @throws Exception 本地 HTTP 服务失败时
     */
    @Test
    void responsesToolLoopAndImage() throws Exception {
        /** 收到的请求体。 */
        List<JsonNode> requests = new ArrayList<>();
        /** 请求轮次。 */
        AtomicInteger round = new AtomicInteger();
        /** 本地 fixture 服务。 */
        HttpServer server = server();
        server.createContext("/responses", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            if (round.incrementAndGet() == 1) respond(exchange, 200,
                    event("{\"type\":\"response.output_item.added\",\"output_index\":1,\"item\":{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"sum\"}}")
                            + event("{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"算\"}")
                            + event("{\"type\":\"response.function_call_arguments.delta\",\"output_index\":1,\"delta\":\"{\\\"a\\\":\"}")
                            + event("{\"type\":\"response.function_call_arguments.done\",\"output_index\":1,\"arguments\":\"{\\\"a\\\":2}\"}")
                            + event("{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"reasoning\",\"encrypted_content\":\"opaque\"}}")
                            + event("{\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"usage\":{\"input_tokens\":8,\"output_tokens\":3,\"output_tokens_details\":{\"reasoning_tokens\":1}}}}"));
            else respond(exchange, 200,
                    event("{\"type\":\"response.output_text.delta\",\"delta\":\"3\"}")
                            + event("{\"type\":\"response.completed\",\"response\":{\"id\":\"r2\",\"usage\":{\"input_tokens\":15,\"output_tokens\":1}}}"));
        });
        server.start();
        try {
            /** 具有图片和推理能力的实例。 */
            MultiProtocolGateway gateway = new MultiProtocolGateway(List.of(config(server, "responses",
                    MultiProtocolModelConfig.Protocol.RESPONSES, true, true, false)));
            /** 含图片的用户消息。 */
            Message user = new Message("u", Role.USER, List.of(new TextContentBlock("计算"),
                    new MediaContentBlock(MediaContentBlock.Kind.IMAGE, "image/png", "x.png", null,
                            new byte[]{1, 2, 3})), List.of(), List.of(), Map.of());
            /** 第一轮事件记录。 */
            Recorder first = new Recorder();
            gateway.generate(new ModelRequest("responses", List.of(user),
                    List.of(new ModelToolDefinition("sum", "求和", "{\"type\":\"object\"}")),
                    new ModelOptions(0.2, 2000, "low")), first);
            assertThat(first.error).isNull();
            assertThat(first.completed).isEqualTo(1);
            assertThat(first.events).extracting(ModelEvent::getClass)
                    .containsExactly(ReasoningDelta.class, UsageReported.class, TurnCompleted.class);
            assertThat(first.turn().getFinishReason()).isEqualTo(ModelFinishReason.TOOL_CALLS);
            assertThat(first.turn().getAssistantMessage().getToolCalls().get(0).getArgumentsJson()).isEqualTo("{\"a\":2}");
            assertThat(first.turn().getAssistantMessage().getProviderMetadata()).containsEntry("reasoning_item_json",
                    "{\"type\":\"reasoning\",\"encrypted_content\":\"opaque\"}");
            assertThat(first.turn().getUsage().getReasoningTokens()).isEqualTo(1L);
            assertThat(requests.get(0).path("input").get(0).path("content").get(1).path("image_url").asText())
                    .startsWith("data:image/png;base64,");
            assertThat(requests.get(0).path("tools").get(0).path("name").asText()).isEqualTo("sum");
            /** 第二轮工具结果。 */
            Message result = new Message("t", Role.TOOL, List.of(), List.of(),
                    List.of(new ToolResult("c1", ToolResultStatus.SUCCESS, "3", null, false)), Map.of());
            /** 第二轮事件记录。 */
            Recorder second = new Recorder();
            gateway.generate(new ModelRequest("responses", List.of(user, first.turn().getAssistantMessage(), result)), second);
            assertThat(second.turn().getFinishReason()).isEqualTo(ModelFinishReason.STOP);
            assertThat(second.events).extracting(ModelEvent::getClass)
                    .containsExactly(TextDelta.class, UsageReported.class, TurnCompleted.class);
            assertThat(requests.get(1).path("input").get(1).path("type").asText()).isEqualTo("reasoning");
            assertThat(requests.get(1).path("input").get(2).path("call_id").asText()).isEqualTo("c1");
            assertThat(requests.get(1).path("input").get(3).path("call_id").asText()).isEqualTo("c1");
            assertThat(requests.get(1).has("temperature")).isFalse();
        } finally { server.stop(0); }
    }

    /**
     * 验证 Anthropic 系统消息、交错增量、签名和工具结果。
     *
     * @throws Exception 本地 HTTP 服务失败时
     */
    @Test
    void anthropicToolLoopAndSignature() throws Exception {
        /** 收到的请求体。 */
        List<JsonNode> requests = new ArrayList<>();
        /** 请求轮次。 */
        AtomicInteger round = new AtomicInteger();
        /** 本地 fixture 服务。 */
        HttpServer server = server();
        server.createContext("/anthropic", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            if (round.incrementAndGet() == 1) respond(exchange, 200,
                    event("{\"type\":\"message_start\",\"message\":{\"id\":\"m1\",\"usage\":{\"input_tokens\":9}}}")
                            + event("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\"}}")
                            + event("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"想\"}}")
                            + event("{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"c1\",\"name\":\"sum\",\"input\":{}}}")
                            + event("{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":2}\"}}")
                            + event("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"sig\"}}")
                            + event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":4}}")
                            + event("{\"type\":\"message_stop\"}"));
            else respond(exchange, 200,
                    event("{\"type\":\"message_start\",\"message\":{\"id\":\"m2\"}}")
                            + event("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\"}}")
                            + event("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"3\"}}")
                            + event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}")
                            + event("{\"type\":\"message_stop\"}"));
        });
        server.start();
        try {
            /** Anthropic 模型实例。 */
            MultiProtocolGateway gateway = new MultiProtocolGateway(List.of(config(server, "anthropic",
                    MultiProtocolModelConfig.Protocol.ANTHROPIC, true, true, false)));
            /** 系统提示词。 */
            Message system = text("s", Role.SYSTEM, "规则");
            /** 用户输入。 */
            Message user = text("u", Role.USER, "计算");
            /** 第一轮事件记录。 */
            Recorder first = new Recorder();
            gateway.generate(new ModelRequest("anthropic", List.of(system, user),
                    List.of(new ModelToolDefinition("sum", "求和", "{\"type\":\"object\"}")),
                    new ModelOptions(null, 6000, "medium")), first);
            assertThat(first.error).isNull();
            assertThat(first.turn().getAssistantMessage().getProviderMetadata())
                    .containsEntry("thinking", "想").containsEntry("thinking_signature", "sig");
            assertThat(first.turn().getFinishReason()).isEqualTo(ModelFinishReason.TOOL_CALLS);
            assertThat(first.turn().getUsage().getTotalTokens()).isEqualTo(13);
            assertThat(requests.get(0).path("system").asText()).isEqualTo("规则");
            assertThat(requests.get(0).path("thinking").path("budget_tokens").asInt()).isEqualTo(2048);
            /** 工具结果消息。 */
            Message result = new Message("t", Role.TOOL, List.of(), List.of(),
                    List.of(new ToolResult("c1", ToolResultStatus.SUCCESS, "3", null, false)), Map.of());
            /** 第二轮事件记录。 */
            Recorder second = new Recorder();
            gateway.generate(new ModelRequest("anthropic", List.of(system, user,
                    first.turn().getAssistantMessage(), result)), second);
            assertThat(second.turn().getFinishReason()).isEqualTo(ModelFinishReason.STOP);
            assertThat(requests.get(1).path("messages").get(1).path("content").get(0).path("signature").asText())
                    .isEqualTo("sig");
            assertThat(requests.get(1).path("messages").get(2).path("content").get(0)
                    .path("tool_use_id").asText()).isEqualTo("c1");
        } finally { server.stop(0); }
    }

    /**
     * 断流和不支持的附件在联网前以错误终止。
     *
     * @throws Exception 本地 HTTP 服务失败时
     */
    @Test
    void rejectsUnsupportedMediaAndBrokenStream() throws Exception {
        /** 请求次数。 */
        AtomicInteger count = new AtomicInteger();
        /** 本地 fixture 服务。 */
        HttpServer server = server();
        server.createContext("/responses", exchange -> {
            count.incrementAndGet();
            respond(exchange, 200, event("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}"));
        });
        server.start();
        try {
            /** 无图片能力的模型。 */
            MultiProtocolGateway gateway = new MultiProtocolGateway(List.of(config(server, "responses",
                    MultiProtocolModelConfig.Protocol.RESPONSES, false, false, false)));
            /** 包含视频的用户消息。 */
            Message media = new Message("u", Role.USER, List.of(new MediaContentBlock(
                    MediaContentBlock.Kind.VIDEO, "video/mp4", "x.mp4", URI.create("https://example.com/x.mp4"), null)),
                    List.of(), List.of(), Map.of());
            /** 能力错误记录。 */
            Recorder unsupported = new Recorder();
            gateway.generate(new ModelRequest("responses", List.of(media)), unsupported);
            assertThat(unsupported.error).isNotNull();
            assertThat(count).hasValue(0);
            /** 断流错误记录。 */
            Recorder broken = new Recorder();
            gateway.generate(new ModelRequest("responses", List.of(text("u2", Role.USER, "hi"))), broken);
            assertThat(broken.error).isNotNull();
            assertThat(broken.completed).isZero();
            assertThat(broken.events).extracting(ModelEvent::getClass).containsExactly(TextDelta.class);
        } finally { server.stop(0); }
    }

    /**
     * 创建测试模型配置。
     *
     * @param server 本地服务
     * @param modelId 本地标识与路径
     * @param protocol 协议
     * @param image 是否允许图片
     * @param reasoning 是否允许推理
     * @param unsigned 是否回传无签名推理
     * @return 模型配置
     */
    private static MultiProtocolModelConfig config(HttpServer server, String modelId,
                                                   MultiProtocolModelConfig.Protocol protocol,
                                                   boolean image, boolean reasoning, boolean unsigned) {
        return new MultiProtocolModelConfig(modelId, protocol,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + modelId),
                "wire", "test-key", ModelOptions.empty(), image, reasoning, unsigned);
    }

    /**
     * 创建文本消息。
     *
     * @param id 消息标识
     * @param role 消息角色
     * @param value 文本
     * @return 文本消息
     */
    private static Message text(String id, Role role, String value) {
        return new Message(id, role, List.of(new TextContentBlock(value)), List.of(), List.of(), Map.of());
    }

    /** @return 随机本地端口的 HTTP 服务 */
    private static HttpServer server() throws IOException { return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }

    /**
     * 包装 JSON 为 SSE 数据事件。
     *
     * @param json JSON 事件
     * @return SSE 事件文本
     */
    private static String event(String json) { return "data: " + json + "\n\n"; }

    /**
     * 返回 fixture 响应。
     *
     * @param exchange HTTP 交换
     * @param status 状态码
     * @param body 响应内容
     * @throws IOException 写响应失败时
     */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        /** UTF-8 响应字节。 */
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    /**
     * 记录模型事件和唯一终态。
     *
     * @author Sebastian
     * @since 2026/10/03
     */
    private static final class Recorder implements ModelEventListener {
        /** 收到的模型事件。 */
        private final List<ModelEvent> events = new ArrayList<>();
        /** 错误终态。 */
        private Throwable error;
        /** 正常终态次数。 */
        private int completed;

        /** @param event 收到的业务事件 */
        @Override public void onEvent(ModelEvent event) { events.add(event); }
        /** @param error 模型错误 */
        @Override public void onError(Throwable error) { this.error = error; }
        /** 记录正常结束。 */
        @Override public void onComplete() { completed++; }
        /** @return 唯一完整回合 */
        private ModelTurn turn() { return ((TurnCompleted) events.get(events.size() - 1)).getTurn(); }
    }
}
