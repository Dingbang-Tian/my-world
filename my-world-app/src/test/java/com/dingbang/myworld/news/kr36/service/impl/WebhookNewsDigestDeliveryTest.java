package com.dingbang.myworld.news.kr36.service.impl;

import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;
import com.dingbang.myworld.news.kr36.properties.Kr36NewsDigestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.adapter.JdkFlowAdapter;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 验证飞书 Webhook 的业务状态检查和通用 Webhook 兼容性。
 *
 * @author Sebastian
 * @since 2026/10/08
 */
@ExtendWith(MockitoExtension.class)
class WebhookNewsDigestDeliveryTest {

    /**
     * 不执行真实网络请求的 HTTP 客户端。
     */
    @Mock
    private HttpClient client;

    /**
     * 包含 HTTP 状态及业务正文的响应。
     */
    @Mock
    private HttpResponse<String> response;

    /**
     * 飞书机器人的测试地址。
     */
    private static final String FEISHU_ENDPOINT = "https://open.feishu.cn/open-apis/bot/v2/hook/test";

    /**
     * 验证 HTTP 200 但飞书业务失败时抛出明确异常。
     *
     * @throws Exception HTTP 客户端替身配置失败
     */
    @Test
    void rejectsBusinessFailureWithHttp200() throws Exception {
        configureResponse(200, "{\"code\":9499,\"msg\":\"Bad Request\",\"data\":{}}");
        assertThatThrownBy(() -> delivery(FEISHU_ENDPOINT).deliver(digest("日报正文")))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("飞书拒绝日报消息: {\"code\":9499,\"msg\":\"Bad Request\",\"data\":{}}");
    }

    /**
     * 验证飞书业务状态为 0 时发送成功，且实际消息包含 tudoubing 关键词。
     *
     * @throws Exception HTTP 客户端替身配置失败
     */
    @Test
    void acceptsFeishuSuccess() throws Exception {
        configureResponse(200, "{\"code\":0,\"msg\":\"success\",\"data\":{}}");
        assertDoesNotThrow(() -> delivery(FEISHU_ENDPOINT).deliver(digest("日报正文")));
        assertThat(sentBody()).isEqualTo(new ObjectMapper().readTree(
                "{\"msg_type\":\"text\",\"content\":{\"text\":\"tudoubing\\n日报正文\"}}"));
    }

    /**
     * 验证缺少飞书业务状态码时不能误判成功。
     *
     * @throws Exception HTTP 客户端替身配置失败
     */
    @Test
    void rejectsMissingBusinessCode() throws Exception {
        configureResponse(200, "{}");
        assertThatThrownBy(() -> delivery(FEISHU_ENDPOINT).deliver(digest("日报正文")))
                .hasRootCauseMessage("飞书拒绝日报消息: {}");
    }

    /**
     * 验证非 JSON 响应不能误判为飞书成功。
     *
     * @throws Exception HTTP 客户端替身配置失败
     */
    @Test
    void rejectsMalformedResponse() throws Exception {
        configureResponse(200, "not-json");
        assertThatThrownBy(() -> delivery(FEISHU_ENDPOINT).deliver(digest("日报正文")))
                .hasStackTraceContaining("飞书业务响应不是有效 JSON");
    }

    /**
     * 验证通用 Webhook 仍按 HTTP 状态判断发送结果。
     *
     * @throws Exception HTTP 客户端替身配置失败
     */
    @Test
    void acceptsGenericWebhook() throws Exception {
        configureResponse(204, null);
        assertDoesNotThrow(() -> delivery("https://receiver.example/digest").deliver(digest("日报正文")));
        assertThat(sentBody()).isEqualTo(new ObjectMapper().readTree(
                "{\"date\":\"2026-10-08\",\"title\":\"2026-10-08 36氪新闻日报\",\"content\":\"日报正文\"}"));
    }

    /**
     * 验证非成功 HTTP 状态不会记作已推送。
     *
     * @throws Exception HTTP 客户端替身配置失败
     */
    @Test
    void rejectsHttpFailure() throws Exception {
        configureResponse(400, null);
        assertThatThrownBy(() -> delivery(FEISHU_ENDPOINT).deliver(digest("日报正文")))
                .hasRootCauseMessage("Webhook HTTP 状态码: 400");
    }

    /**
     * 验证超出飞书大小限制的消息在发出请求前被拒绝。
     */
    @Test
    void rejectsOversizedMessage() {
        assertThatThrownBy(() -> delivery(FEISHU_ENDPOINT).deliver(digest("新".repeat(8_000))))
                .hasStackTraceContaining("飞书消息超过 20 KB 限制");
        verifyNoInteractions(client);
    }

    /**
     * 配置本次测试使用的 HTTP 响应。
     *
     * @param status HTTP 状态码
     * @param body 飞书响应正文；空值表示本用例不会读取响应正文
     * @throws Exception HTTP 客户端替身配置失败
     */
    private void configureResponse(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        if (body != null) {
            when(response.body()).thenReturn(body);
        }
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
    }

    /**
     * 读取实际提交给 HTTP 客户端的短消息请求体。
     *
     * @return 请求体的 JSON 数据
     * @throws Exception 请求捕获或 JSON 解析失败
     */
    private JsonNode sentBody() throws Exception {
        /**
         * 捕获推送器创建的真实 HTTP 请求。
         */
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        /**
         * 将短消息的请求体转换为 JSON 文本。
         */
        String json = JdkFlowAdapter.flowPublisherToFlux(captor.getValue().bodyPublisher().orElseThrow())
                .map(buffer -> StandardCharsets.UTF_8.decode(buffer).toString())
                .collect(Collectors.joining()).block();
        return new ObjectMapper().readTree(json);
    }

    /**
     * 创建使用替身客户端的推送器。
     *
     * @param endpoint 目标地址
     * @return 待验证的推送器
     */
    private WebhookNewsDigestDelivery delivery(String endpoint) {
        /**
         * 测试渠道地址和飞书关键词。
         */
        Kr36NewsDigestProperties properties = new Kr36NewsDigestProperties();
        properties.setWebhookUrl(endpoint);
        properties.setFeishuKeyword("tudoubing");
        return new WebhookNewsDigestDelivery(properties, new ObjectMapper(), client);
    }

    /**
     * 创建待推送的测试日报。
     *
     * @param content 日报正文
     * @return 测试日报
     */
    private Kr36NewsDigestResp digest(String content) {
        return new Kr36NewsDigestResp(LocalDate.of(2026, 10, 8), content, List.of());
    }
}
