package com.dingbang.myworld.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 记录 HTTP 请求和响应的统一访问日志切片。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class HttpAccessLogFilter extends OncePerRequestFilter {

    /**
     * 访问日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpAccessLogFilter.class);

    /**
     * 请求链路标识请求头名称。
     */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * 日志中允许记录的单个请求或响应体最大字符数。
     */
    private static final int MAX_BODY_LENGTH = 16 * 1024;

    /**
     * 需要脱敏的请求头名称。
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "api-key", "x-api-key", "password", "secret", "token");

    /**
     * 需要脱敏的 JSON 字段名称模式。
     */
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)(\\\"(?:password|api[_-]?key|token|secret|authorization|cookie|master[_-]?key)"
                    + "\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");

    /**
     * 需要脱敏的查询参数名称模式。
     */
    private static final Pattern SENSITIVE_QUERY_PARAMETER = Pattern.compile(
            "(?i)((?:^|&)(?:password|api[_-]?key|token|secret|authorization|cookie|master[_-]?key)"
                    + "=)[^&]*");

    /**
     * 记录一次 HTTP 请求的完整访问信息。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain Servlet 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException IO 处理异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        /**
         * 可重复读取请求体的包装请求。
         */
        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request, MAX_BODY_LENGTH);
        /**
         * 可读取响应体的包装响应。
         */
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        /**
         * 当前请求的链路标识。
         */
        String requestId = resolveRequestId(request);
        /**
         * 当前请求开始时间的纳秒值。
         */
        long startNanos = System.nanoTime();
        cachingResponse.setHeader(REQUEST_ID_HEADER, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            filterChain.doFilter(cachingRequest, cachingResponse);
        } finally {
            cachingResponse.copyBodyToResponse();
            /**
             * 请求执行耗时，单位为毫秒。
             */
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            logAccess(cachingRequest, cachingResponse, requestId, durationMillis);
        }
    }

    /**
     * 生成或复用请求链路标识。
     *
     * @param request 当前 HTTP 请求
     * @return 请求链路标识
     */
    private String resolveRequestId(HttpServletRequest request) {
        /**
         * 客户端提供的请求链路标识。
         */
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * 输出请求、响应和耗时日志。
     *
     * @param request 包装后的请求
     * @param response 包装后的响应
     * @param requestId 请求链路标识
     * @param durationMillis 请求耗时，单位为毫秒
     */
    private void logAccess(ContentCachingRequestWrapper request,
                           ContentCachingResponseWrapper response,
                           String requestId, long durationMillis) {
        /**
         * 请求查询参数。
         */
        String query = sanitizeQuery(request.getQueryString());
        /**
         * 请求头摘要。
         */
        String headers = sanitizeHeaders(request);
        /**
         * 请求体摘要。
         */
        String requestBody = readBody(request.getContentAsByteArray(), request.getContentType(),
                request.getCharacterEncoding());
        /**
         * 响应体摘要。
         */
        String responseBody = readBody(response.getContentAsByteArray(), response.getContentType(),
                response.getCharacterEncoding());
        LOGGER.info("http_access requestId={} method={} uri={} query={} headers={} requestBody={} "
                        + "status={} responseBody={} durationMs={}", requestId, request.getMethod(),
                request.getRequestURI(), query, headers, requestBody, response.getStatus(), responseBody,
                durationMillis);
    }

    /**
     * 脱敏并拼接请求头。
     *
     * @param request 当前 HTTP 请求
     * @return 请求头摘要
     */
    private String sanitizeHeaders(HttpServletRequest request) {
        /**
         * 已处理的请求头名称，避免同一名称重复记录。
         */
        Set<String> names = new HashSet<>();
        /**
         * 请求头摘要拼接器。
         */
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!names.add(name.toLowerCase())) {
                return;
            }
            /**
             * 当前请求头值。
             */
            String value = SENSITIVE_HEADERS.contains(name.toLowerCase())
                    ? "***" : sanitizeText(request.getHeader(name));
            joiner.add(name + "=" + value);
        });
        return joiner.toString();
    }

    /**
     * 脱敏查询参数。
     *
     * @param query 原始查询参数
     * @return 脱敏后的查询参数
     */
    private String sanitizeQuery(String query) {
        return query == null || query.isBlank() ? "" : sanitizeText(query);
    }

    /**
     * 读取并脱敏请求或响应体。
     *
     * @param content 内容字节
     * @param contentType 内容类型
     * @param encoding 字符编码
     * @return 脱敏后的内容摘要
     */
    private String readBody(byte[] content, String contentType, String encoding) {
        if (content == null || content.length == 0) {
            return "";
        }
        if (!isTextual(contentType)) {
            return "<binary contentType=" + contentType + ", length=" + content.length + ">";
        }
        /**
         * 请求或响应使用的字符集。
         */
        Charset charset = encoding == null || encoding.isBlank()
                ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        /**
         * 请求或响应文本内容。
         */
        String text = new String(content, charset);
        return sanitizeText(text);
    }

    /**
     * 判断内容是否适合写入文本日志。
     *
     * @param contentType 内容类型
     * @return 文本、JSON、表单或 XML 内容返回 true
     */
    private boolean isTextual(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.toLowerCase();
        return normalized.startsWith("text/") || normalized.contains("json")
                || normalized.contains("xml") || normalized.contains("form-urlencoded")
                || normalized.contains("javascript");
    }

    /**
     * 对文本中的敏感字段进行脱敏并限制日志长度。
     *
     * @param value 原始文本
     * @return 脱敏并截断后的文本
     */
    private String sanitizeText(String value) {
        /**
         * JSON 字段脱敏后的文本。
         */
        String sanitized = SENSITIVE_JSON_FIELD.matcher(value).replaceAll("$1***$2");
        sanitized = SENSITIVE_QUERY_PARAMETER.matcher(sanitized).replaceAll("$1***");
        if (sanitized.length() <= MAX_BODY_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_BODY_LENGTH) + "...[truncated]";
    }
}
