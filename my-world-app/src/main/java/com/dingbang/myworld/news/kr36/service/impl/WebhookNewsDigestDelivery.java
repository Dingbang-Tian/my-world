package com.dingbang.myworld.news.kr36.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingbang.myworld.news.kr36.entity.dto.FeishuTextContentDTO;
import com.dingbang.myworld.news.kr36.entity.req.FeishuTextMessageReq;
import com.dingbang.myworld.news.kr36.entity.resp.FeishuWebhookResp;
import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;
import com.dingbang.myworld.news.kr36.service.NewsDigestDelivery;
import com.dingbang.myworld.common.utils.lang.ObjectUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import com.dingbang.myworld.news.kr36.properties.Kr36NewsDigestProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 通过通用 JSON Webhook 或飞书自定义机器人推送新闻日报。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Slf4j
public class WebhookNewsDigestDelivery implements NewsDigestDelivery {

    /**
     * 飞书机器人请求体的最大字节数，单位为字节。
     */
    private static final int MAX_FEISHU_MESSAGE_BYTES = 20 * 1024;

    /**
     * Webhook 目标地址。
     */
    private final URI endpoint;

    /**
     * 飞书消息正文需要包含的自定义关键词。
     */
    private final String feishuKeyword;

    /**
     * HTTP 客户端。
     */
    private final HttpClient client;

    /**
     * JSON 编码器。
     */
    private final ObjectMapper mapper;

    /**
     * 创建 Webhook 推送器。
     *
     * @param properties 日报推送地址和飞书关键词配置
     * @param mapper JSON 编码器
     */
    public WebhookNewsDigestDelivery(Kr36NewsDigestProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /**
     * 创建可注入 HTTP 客户端的 Webhook 推送器。
     *
     * @param properties 日报推送地址和飞书关键词配置
     * @param mapper JSON 编码器
     * @param client HTTP 客户端
     */
    WebhookNewsDigestDelivery(Kr36NewsDigestProperties properties, ObjectMapper mapper, HttpClient client) {
        this.endpoint = URI.create(properties.getWebhookUrl());
        if (!"https".equalsIgnoreCase(this.endpoint.getScheme())) {
            throw new IllegalArgumentException("Webhook 地址必须使用 HTTPS");
        }
        this.mapper = mapper;
        this.client = client;
        this.feishuKeyword = StringUtils.trimToEmpty(properties.getFeishuKeyword());
    }

    /**
     * 发送适配目标渠道的 JSON，并检查 HTTP 状态及飞书业务状态。
     *
     * @param digest 待推送日报
     * @throws IllegalStateException 消息超限、请求失败或飞书拒绝消息
     */
    @Override
    public void deliver(Kr36NewsDigestResp digest) {
        try {
            /**
             * 与目标渠道匹配的 JSON 请求体。
             */
            String json = mapper.writeValueAsString(isFeishuWebhook()
                    ? new FeishuTextMessageReq("text", new FeishuTextContentDTO(feishuMessageText(digest)))
                    : Map.of("date", digest.getDate().toString(),
                            "title", digest.getDate() + " 36氪新闻日报", "content", digest.getContent()));
            /**
             * 请求体使用 UTF-8 编码后的字节数。
             */
            int payloadBytes = json.getBytes(StandardCharsets.UTF_8).length;
            if (isFeishuWebhook() && payloadBytes > MAX_FEISHU_MESSAGE_BYTES) {
                throw new IllegalStateException("飞书消息超过 20 KB 限制，实际字节数: " + payloadBytes);
            }
            log.info("开始推送日报: 日期 {}, 渠道 {}, 目标主机 {}, 请求 {} 字节", digest.getDate(),
                    isFeishuWebhook() ? "飞书" : "通用 Webhook", endpoint.getHost(), payloadBytes);
            /**
             * 本次发送请求。
             */
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Idempotency-Key", "36kr-digest-" + digest.getDate())
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            /**
             * 包含业务响应正文的 HTTP 响应。
             */
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("Webhook HTTP 响应: 目标主机 {}, 状态 {}", endpoint.getHost(), response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Webhook HTTP 状态码: " + response.statusCode());
            }
            if (isFeishuWebhook()) {
                validateFeishuResponse(response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook 推送被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("新闻日报 Webhook 推送失败", exception);
        }
    }

    /**
     * 将机器人要求的关键词添加到飞书消息正文开头。
     *
     * @param digest 待发送日报
     * @return 含自定义关键词的消息文本
     */
    private String feishuMessageText(Kr36NewsDigestResp digest) {
        return StringUtils.isBlank(feishuKeyword) ? digest.getContent() : feishuKeyword + "\n" + digest.getContent();
    }

    /**
     * 判断目标是否为飞书自定义机器人的 Webhook。
     *
     * @return 是否需要使用飞书请求和响应格式
     */
    private boolean isFeishuWebhook() {
        return "open.feishu.cn".equalsIgnoreCase(endpoint.getHost())
                && endpoint.getPath().startsWith("/open-apis/bot/v2/hook/");
    }

    /**
     * 检查飞书业务响应，避免将 HTTP 200 的业务失败标记为已推送。
     *
     * @param body 飞书返回的 JSON 文本
     * @throws IllegalStateException 响应无法解析、缺少业务状态码或业务失败
     */
    private void validateFeishuResponse(String body) {
        try {
            /**
             * 飞书返回的业务结果。
             */
            FeishuWebhookResp result = mapper.readValue(body, FeishuWebhookResp.class);
            if (ObjectUtils.isNull(result) || !Integer.valueOf(0).equals(result.getCode())) {
                throw new IllegalStateException("飞书拒绝日报消息: " + body);
            }
            log.info("飞书业务响应: code={}, msg={}", result.getCode(), result.getMsg());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("飞书业务响应不是有效 JSON", exception);
        }
    }
}
