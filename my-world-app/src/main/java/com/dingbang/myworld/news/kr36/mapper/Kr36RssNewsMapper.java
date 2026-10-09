package com.dingbang.myworld.news.kr36.mapper;

import com.dingbang.myworld.news.kr36.entity.dto.Kr36NewsItemDTO;
import com.dingbang.myworld.news.kr36.properties.Kr36NewsDigestProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * 读取 36氪官方 RSS 并转换为新闻条目。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Slf4j
public class Kr36RssNewsMapper {

    /**
     * 单个 RSS 响应允许读取的最大字节数。
     */
    private static final int MAX_FEED_BYTES = 2_000_000;

    /**
     * 36氪 RSS 使用的带时区偏移的发布时间格式。
     */
    private static final DateTimeFormatter KR36_DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss Z", Locale.ROOT);

    /**
     * RSS 请求客户端。
     */
    private final HttpClient client;

    /**
     * 日报配置。
     */
    private final Kr36NewsDigestProperties properties;

    /**
     * 创建官方 RSS 读取器。
     *
     * @param properties 日报配置
     */
    public Kr36RssNewsMapper(Kr36NewsDigestProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    /**
     * 创建可注入 HTTP 客户端的 RSS 读取器。
     *
     * @param properties 日报配置
     * @param client HTTP 客户端
     */
    Kr36RssNewsMapper(Kr36NewsDigestProperties properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 从已配置的订阅源读取新闻，单个源失败时保留其他源的结果。
     *
     * @return 成功读取的新闻
     */
    public List<Kr36NewsItemDTO> read() {
        // 初始化合并后的新闻。
        List<Kr36NewsItemDTO> articles = new ArrayList<>();
        // 记录成功读取的订阅源数量。
        int successCount = 0;
        for (String feedUrl : properties.getFeedUrls()) {
            try {
                // 解析当前订阅源地址。
                URI uri = URI.create(feedUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !is36KrHost(uri.getHost())) {
                    throw new IllegalArgumentException("RSS 地址必须为 36kr.com 的 HTTPS 地址");
                }
                // 创建当前请求。
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/rss+xml, application/xml, text/xml")
                        .header("User-Agent", "myWorld-news-digest/1.0")
                        .GET().build();
                // 获取当前响应。
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (!"https".equalsIgnoreCase(response.uri().getScheme())
                            || !is36KrHost(response.uri().getHost())) {
                        throw new IllegalStateException("RSS 重定向到非 36氪 HTTPS 地址");
                    }
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("RSS HTTP 状态码: " + response.statusCode());
                    }
                    /**
                     * 当前响应的媒体类型，用于识别安全检测页面。
                     */
                    String contentType = response.headers().firstValue("Content-Type").orElse("");
                    if (contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                        throw new IllegalStateException("36氪返回 HTML 页面，可能是安全检测页；RSS 应使用 www.36kr.com，响应地址: "
                                + response.uri());
                    }
                    // 最多读取上限加一个字节，用于识别超长响应。
                    byte[] bytes = body.readNBytes(MAX_FEED_BYTES + 1);
                    if (bytes.length > MAX_FEED_BYTES) throw new IllegalStateException("RSS 内容超过大小限制");
                    /**
                     * 当前订阅源成功解析的新闻。
                     */
                    List<Kr36NewsItemDTO> feedItems = parse(bytes);
                    articles.addAll(feedItems);
                    log.info("36氪 RSS 读取完成: {}, 响应 {} 字节，新闻 {} 条", response.uri(), bytes.length, feedItems.size());
                    successCount++;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("RSS 读取被中断", exception);
            } catch (Exception exception) {
                log.warn("读取 36氪 RSS 失败: {}", feedUrl, exception);
            }
        }
        if (successCount == 0) throw new IllegalStateException("所有 36氪 RSS 订阅源均读取失败");
        return articles;
    }

    /**
     * 解析 RSS XML，拒绝 HTML 页面并忽略日期或链接无效的条目。
     *
     * @param bytes RSS XML 字节
     * @return 新闻列表
     * @throws IllegalStateException 响应不是 RSS 文档或 XML 解析失败
     */
    public List<Kr36NewsItemDTO> parse(byte[] bytes) {
        /**
         * 响应开头的文本，用于在 XML 解析前识别 HTML 页面。
         */
        String prefix = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.UTF_8)
                .stripLeading().toLowerCase(Locale.ROOT);
        if (prefix.startsWith("<!doctype html") || prefix.startsWith("<html")) {
            throw new IllegalStateException("36氪返回 HTML 页面，可能是安全检测页，无法作为 RSS 解析");
        }
        try {
            // 创建已禁用外部实体的 XML 解析器工厂。
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // 解析 RSS 文档。
            Document document = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
            if (!"rss".equals(document.getDocumentElement().getTagName())) {
                throw new IllegalStateException("36氪响应的 XML 根元素不是 rss");
            }
            // 获取文档中的新闻节点。
            NodeList nodes = document.getElementsByTagName("item");
            // 初始化有效新闻列表。
            List<Kr36NewsItemDTO> articles = new ArrayList<>();
            IntStream.range(0, nodes.getLength()).forEach(index -> {
                // 读取当前新闻节点。
                Element item = (Element) nodes.item(index);
                // 读取新闻标题。
                String title = clean(childText(item, "title"));
                if (title.length() > 200) title = title.substring(0, 200);
                // 读取新闻原文链接。
                String link = childText(item, "link").trim();
                // 读取新闻发布时间。
                Instant publishedAt = parseDate(childText(item, "pubDate"));
                if (title.isBlank() || publishedAt == null || !validArticleLink(link)) return;
                // 读取 RSS 中提供的摘要。
                String description = clean(childText(item, "description"));
                articles.add(new Kr36NewsItemDTO(title, link, description, publishedAt));
            });
            return articles;
        } catch (Exception exception) {
            throw new IllegalStateException("36氪 RSS XML 解析失败", exception);
        }
    }

    /**
     * 读取指定子元素的文本。
     *
     * @param parent 父元素
     * @param name 子元素名称
     * @return 子元素文本，不存在时为空串
     */
    private String childText(Element parent, String name) {
        // 获取子元素节点。
        NodeList children = parent.getChildNodes();
        return IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(child -> child instanceof Element && name.equals(child.getNodeName()))
                .map(Node::getTextContent)
                .findFirst()
                .orElse("");
    }

    /**
     * 清理 RSS 中的 HTML 标记和空白。
     *
     * @param text 原始文本
     * @return 纯文本
     */
    private String clean(String text) {
        return HtmlUtils.htmlUnescape(text.replaceAll("(?s)<[^>]*>", " "))
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * 解析 36氪日期、标准 RSS 日期及 ISO 时间格式的发布时间。
     *
     * @param value RSS 日期文本
     * @return 发布时间，格式无效时为空
     */
    private Instant parseDate(String value) {
        /**
         * 合并连续空白后的发布时间文本。
         */
        String dateText = value.trim().replaceAll("\\s+", " ");
        try {
            return ZonedDateTime.parse(dateText, KR36_DATE_FORMAT).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试标准 RSS 日期。
        }
        try {
            return ZonedDateTime.parse(dateText, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试 ISO 时间。
        }
        try {
            return Instant.parse(dateText);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    /**
     * 判断文章链接是否指向 36氪 HTTPS 页面。
     *
     * @param link 待检查链接
     * @return 是否有效
     */
    private boolean validArticleLink(String link) {
        try {
            // 解析待检查的链接地址。
            URI uri = URI.create(link);
            return "https".equalsIgnoreCase(uri.getScheme()) && is36KrHost(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 判断主机是否属于 36氪域名。
     *
     * @param host 主机名
     * @return 是否属于 36氪
     */
    private boolean is36KrHost(String host) {
        return host != null && (host.equalsIgnoreCase("36kr.com")
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".36kr.com"));
    }
}
