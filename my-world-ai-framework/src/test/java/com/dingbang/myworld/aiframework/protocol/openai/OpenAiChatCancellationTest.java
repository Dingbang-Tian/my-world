package com.dingbang.myworld.aiframework.protocol.openai;

import com.dingbang.myworld.aiframework.api.CancellationToken;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;
import com.dingbang.myworld.aiframework.api.ModelExecutionContext;
import com.dingbang.myworld.aiframework.api.ModelOptions;
import com.dingbang.myworld.aiframework.api.ModelRequest;
import com.dingbang.myworld.aiframework.api.event.ModelEventListener;
import com.dingbang.myworld.aiframework.model.Message;
import com.dingbang.myworld.aiframework.model.Role;
import com.dingbang.myworld.aiframework.model.content.TextContentBlock;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过本机 HTTP 流验证取消传播和响应内存预算。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class OpenAiChatCancellationTest {
    /**
     * 验证取消能关闭正在等待下一段 SSE 的单次模型调用。
     *
     * @throws Exception HTTP fixture 或异步等待失败时
     */
    @Test
    void cancellationClosesOpenSseStream() throws Exception {
        // 本地动态端口 HTTP 服务。
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 服务端首段内容已经发出的同步门闩。
        CountDownLatch sent = new CountDownLatch(1);
        // 测试结束时允许服务端关闭连接。
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                // 首段增量事件。
                byte[] first = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"a\"}}]}\n\n"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseBody().write(first);
                exchange.getResponseBody().flush();
                sent.countDown();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        // 单次网关调用线程。
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 与网关共享的取消令牌。
            CancellationToken token = new CancellationToken();
            // 记录唯一错误回调。
            AtomicReference<Throwable> error = new AtomicReference<>();
            // 本地模型网关。
            OpenAiChatGateway gateway = gateway(server);
            // 正在读取 SSE 的单次请求。
            Future<?> active = executor.submit(() -> gateway.generate(request(token, 100), listener(error)));
            assertThat(sent.await(2, TimeUnit.SECONDS)).isTrue();
            token.cancel();
            active.get(3, TimeUnit.SECONDS);
            assertThat(error.get()).isInstanceOf(ExecutionControlException.class);
            assertThat(((ExecutionControlException) error.get()).getCode()).isEqualTo("CANCELLED");
        } finally {
            release.countDown();
            executor.shutdownNow();
            server.stop(0);
        }
    }

    /**
     * 验证模型分片超过允许字符数时流以预算错误结束。
     *
     * @throws Exception 本机 HTTP fixture 失败时
     */
    @Test
    void oversizedStreamingContentFailsBeforeAccumulation() throws Exception {
        // 本地动态端口 HTTP 服务。
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            // 三字符模型增量和完成标记。
            byte[] body = ("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"abc\"},"
                    + "\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            // 模型调用的错误回调。
            AtomicReference<Throwable> error = new AtomicReference<>();
            gateway(server).generate(request(new CancellationToken(), 2), listener(error));
            assertThat(error.get()).isInstanceOf(ExecutionControlException.class);
            assertThat(((ExecutionControlException) error.get()).getCode()).isEqualTo("LIMIT_EXCEEDED");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 注册指向本机 fixture 的网关。
     *
     * @param server 本地服务
     * @return 单次 Chat 网关
     */
    private static OpenAiChatGateway gateway(HttpServer server) {
        return new OpenAiChatGateway(Collections.singletonList(new OpenAiChatModelConfig("local", "fixture",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"),
                "fixture", "test-only-key", ModelOptions.empty())));
    }

    /**
     * 生成带取消和输出限制的直接网关请求。
     *
     * @param token 共享取消令牌
     * @param output 最大输出字符数
     * @return 模型请求
     */
    private static ModelRequest request(CancellationToken token, int output) {
        // 用户消息。
        Message user = new Message("user", Role.USER,
                Collections.singletonList(new TextContentBlock("hi")), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap());
        return new ModelRequest("local", Collections.singletonList(user), Collections.emptyList(),
                ModelOptions.empty(), new ModelExecutionContext(Instant.now().plusSeconds(5), token, output));
    }

    /**
     * 创建只记录错误的单次模型监听器。
     *
     * @param error 错误容器
     * @return 模型监听器
     */
    private static ModelEventListener listener(AtomicReference<Throwable> error) {
        return new ErrorRecordingModelListener(error);
    }
}
