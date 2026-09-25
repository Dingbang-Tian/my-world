package com.dingbang.myworld.common.util;

import cn.hutool.core.util.StrUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OkHttp请求工具
 *
 * @author dingbang.tian
 * @since 2026/09/22
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OkHttpUtils {

    /**
     * 连接超时时间，单位秒
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /**
     * 读取超时时间，单位秒
     */
    private static final int READ_TIMEOUT_SECONDS = 30;

    /**
     * 写入超时时间，单位秒
     */
    private static final int WRITE_TIMEOUT_SECONDS = 30;

    /**
     * JSON请求类型
     */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    /**
     * 共享客户端，复用连接池
     */
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    /**
     * 发送GET请求
     *
     * @param url
     * @return responseBody
     */
    public static String get(String url) {
        return get(url, Collections.emptyMap());
    }

    /**
     * 发送带请求头的GET请求
     *
     * @param url
     * @param headers
     * @return responseBody
     */
    public static String get(String url, Map<String, String> headers) {
        Request request = baseRequest(url, headers).get().build();
        return execute(request);
    }

    /**
     * 发送JSON POST请求
     *
     * @param url
     * @param body
     * @return responseBody
     */
    public static String postJson(String url, Object body) {
        return postJson(url, body, Collections.emptyMap());
    }

    /**
     * 发送带请求头的JSON POST请求
     *
     * @param url
     * @param body
     * @param headers
     * @return responseBody
     */
    public static String postJson(String url, Object body, Map<String, String> headers) {
        Request request = baseRequest(url, headers)
                .post(jsonBody(body))
                .build();
        return execute(request);
    }

    /**
     * 发送JSON PUT请求
     *
     * @param url
     * @param body
     * @return responseBody
     */
    public static String putJson(String url, Object body) {
        return putJson(url, body, Collections.emptyMap());
    }

    /**
     * 发送带请求头的JSON PUT请求
     *
     * @param url
     * @param body
     * @param headers
     * @return responseBody
     */
    public static String putJson(String url, Object body, Map<String, String> headers) {
        Request request = baseRequest(url, headers)
                .put(jsonBody(body))
                .build();
        return execute(request);
    }

    /**
     * 发送DELETE请求
     *
     * @param url
     * @return responseBody
     */
    public static String delete(String url) {
        return delete(url, Collections.emptyMap());
    }

    /**
     * 发送带请求头的DELETE请求
     *
     * @param url
     * @param headers
     * @return responseBody
     */
    public static String delete(String url, Map<String, String> headers) {
        Request request = baseRequest(url, headers).delete().build();
        return execute(request);
    }

    /**
     * 构造基础请求
     *
     * @param url
     * @param headers
     * @return requestBuilder
     */
    private static Request.Builder baseRequest(String url, Map<String, String> headers) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("Request url must not be blank");
        }
        return new Request.Builder()
                .url(url)
                .headers(Headers.of(headers == null ? Collections.emptyMap() : headers));
    }

    /**
     * 将对象序列化为JSON请求体
     *
     * @param body
     * @return requestBody
     */
    private static RequestBody jsonBody(Object body) {
        String json = body instanceof String jsonText ? jsonText : JsonUtils.toJson(body);
        return RequestBody.create(json, JSON_MEDIA_TYPE);
    }

    /**
     * 执行请求并返回响应文本
     *
     * @param request
     * @return responseBody
     */
    private static String execute(Request request) {
        log.info("OkHttp request, method={}, url={}", request.method(), request.url());
        try (Response response = CLIENT.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                log.warn("OkHttp response failed, method={}, url={}, status={}, body={}",
                        request.method(), request.url(), response.code(), responseBody);
                throw new IllegalStateException("HTTP request failed, status=" + response.code());
            }
            log.info("OkHttp response success, method={}, url={}, status={}",
                    request.method(), request.url(), response.code());
            return responseBody;
        } catch (IOException exception) {
            log.error("OkHttp request error, method={}, url={}", request.method(), request.url(), exception);
            throw new IllegalStateException("HTTP request failed", exception);
        }
    }

    /**
     * 读取响应体
     *
     * @param response
     * @return responseBody
     */
    private static String readBody(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        return responseBody == null ? StrUtil.EMPTY : responseBody.string();
    }
}
