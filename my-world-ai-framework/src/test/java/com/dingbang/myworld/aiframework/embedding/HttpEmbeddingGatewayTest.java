package com.dingbang.myworld.aiframework.embedding;

import com.dingbang.myworld.aiframework.api.ModelGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用本地协议 fixture 验证文本和多模态向量请求与结果。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class HttpEmbeddingGatewayTest {
    /**
     * JSON 测试解析器。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 验证 OpenAI 批量文本输入、索引排序、维度和用量。
     *
     * @throws Exception 本地服务失败时
     */
    @Test
    void openAiBatchIndexesAndUsage() throws Exception {
        // 收到的请求体。
        List<JsonNode> requests = new ArrayList<>();
        // 本地 fixture 服务。
        HttpServer server = server();
        server.createContext("/openai", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "{\"data\":[{\"index\":1,\"embedding\":[0.3,0.4]},{\"index\":0,\"embedding\":[0.1,0.2]}],\"usage\":{\"prompt_tokens\":7,\"total_tokens\":7}}");
        });
        server.start();
        try {
            // 当前模型网关。
            HttpEmbeddingGateway gateway = gateway(server);
            // 两个文本输入的响应。
            EmbeddingResult result = gateway.embed(new EmbeddingRequest("text",
                    List.of(EmbeddingInput.text("甲"), EmbeddingInput.text("乙")), 2, false));
            assertThat(result.getVectors()).extracting(EmbeddingVector::getIndex).containsExactly(0, 1);
            assertThat(result.getVectors().get(0).getValues()).containsExactly(0.1, 0.2);
            assertThat(result.getVectors().get(0).getDimension()).isEqualTo(2);
            assertThat(result.getInputTokens()).isEqualTo(7L);
            assertThat(result.getUsage().getTotalTokens()).isEqualTo(7L);
            assertThat(requests.get(0).path("input").get(0).asText()).isEqualTo("甲");
            assertThat(requests.get(0).path("dimensions").asInt()).isEqualTo(2);
        } finally { server.stop(0); }
    }

    /**
     * 验证多模态 URL、data URI、内存图片及融合参数。
     *
     * @throws Exception 本地服务失败时
     */
    @Test
    void dashScopeFusionAndMediaSources() throws Exception {
        // 收到的请求体。
        List<JsonNode> requests = new ArrayList<>();
        // 本地 fixture 服务。
        HttpServer server = server();
        server.createContext("/dash", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "{\"request_id\":\"d1\",\"output\":{\"embeddings\":[{\"index\":0,\"type\":\"fusion\",\"embedding\":[0.5,0.6]}]},\"usage\":{\"input_tokens\":4,\"image_tokens\":5,\"total_tokens\":9}}");
        });
        server.start();
        try {
            // 当前模型网关。
            HttpEmbeddingGateway gateway = gateway(server);
            // 含文本、内存图片与远程视频的语义单元。
            EmbeddingInput input = new EmbeddingInput(List.of(new EmbeddingPart(EmbeddingInputType.TEXT, "画面"),
                    EmbeddingInput.media(EmbeddingInputType.IMAGE, "image/png", new byte[]{1, 2}),
                    new EmbeddingPart(EmbeddingInputType.VIDEO, "https://example.com/clip.mp4")));
            // 融合结果。
            EmbeddingResult result = gateway.embed(new EmbeddingRequest("multi", List.of(input), 2, true));
            assertThat(result.getVectors().get(0).getType()).isEqualTo("fusion");
            assertThat(result.getRequestId()).isEqualTo("d1");
            assertThat(result.getInputTokens()).isEqualTo(4L);
            assertThat(result.getUsage().getImageTokens()).isEqualTo(5L);
            assertThat(result.getUsage().getTotalTokens()).isEqualTo(9L);
            assertThat(requests.get(0).path("input").path("contents").get(1).path("image").asText())
                    .startsWith("data:image/png;base64,");
            assertThat(requests.get(0).path("input").path("contents").get(2).path("video").asText())
                    .isEqualTo("https://example.com/clip.mp4");
            assertThat(requests.get(0).path("parameters").path("enable_fusion").asBoolean()).isTrue();
        } finally { server.stop(0); }
    }

    /**
     * 验证能力拒绝、无效结果和 HTTP 错误。
     *
     * @throws Exception 本地服务失败时
     */
    @Test
    void rejectsMediaBeforeNetworkAndInvalidResponses() throws Exception {
        // 网络请求计数。
        AtomicInteger calls = new AtomicInteger();
        // 返回状态码。
        AtomicInteger status = new AtomicInteger(200);
        // 本地 fixture 服务。
        HttpServer server = server();
        server.createContext("/openai", exchange -> {
            calls.incrementAndGet();
            respond(exchange, status.get(), "{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}");
        });
        server.start();
        try {
            // 当前模型网关。
            HttpEmbeddingGateway gateway = gateway(server);
            // 图片输入。
            EmbeddingInput media = new EmbeddingInput(List.of(new EmbeddingPart(
                    EmbeddingInputType.IMAGE, "https://example.com/x.png")));
            assertThatThrownBy(() -> gateway.embed(new EmbeddingRequest("text", List.of(media), null, false)))
                    .isInstanceOf(ModelGatewayException.class).hasMessageContaining("只接受独立文本");
            assertThat(calls).hasValue(0);
            assertThatThrownBy(() -> gateway.embed(new EmbeddingRequest("text",
                    List.of(EmbeddingInput.text("hi")), 2, false)))
                    .isInstanceOf(ModelGatewayException.class).hasMessageContaining("维度");
            status.set(429);
            assertThatThrownBy(() -> gateway.embed(new EmbeddingRequest("text",
                    List.of(EmbeddingInput.text("hi")), null, false)))
                    .isInstanceOf(ModelGatewayException.class).hasMessageContaining("429");
            assertThatThrownBy(() -> new EmbeddingPart(EmbeddingInputType.IMAGE, "file:///tmp/x.png"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally { server.stop(0); }
    }

    /**
     * 创建两个协议实例的测试网关。
     *
     * @param server 本地 HTTP 服务
     * @return 双模型向量网关
     */
    private static HttpEmbeddingGateway gateway(HttpServer server) {
        // 测试服务的主机端口。
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new HttpEmbeddingGateway(List.of(
                new EmbeddingModelConfig("text", EmbeddingProtocol.OPENAI,
                        URI.create(base + "/openai"), "text-wire", "key", null),
                new EmbeddingModelConfig("multi", EmbeddingProtocol.DASHSCOPE,
                        URI.create(base + "/dash"), "qwen3-vl-embedding", "key", null, true, true)));
    }

    /**
     * 创建本地随机端口服务。
     *
     * @return HTTP fixture 服务
     * @throws IOException 端口无法绑定时
     */
    private static HttpServer server() throws IOException { return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }

    /**
     * 写 fixture 响应。
     *
     * @param exchange 当前 HTTP 交换
     * @param status 状态码
     * @param body 响应文本
     * @throws IOException 发送失败时
     */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        // UTF-8 响应内容。
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
