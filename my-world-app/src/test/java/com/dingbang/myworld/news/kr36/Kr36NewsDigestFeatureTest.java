package com.dingbang.myworld.news.kr36;

import com.dingbang.myworld.ai.application.AiChatResponse;
import com.dingbang.myworld.ai.application.AiChatRequest;
import com.dingbang.myworld.ai.application.AiChatService;
import com.dingbang.myworld.news.kr36.entity.dto.Kr36NewsItemDTO;
import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;
import com.dingbang.myworld.news.kr36.mapper.Kr36RssNewsMapper;
import com.dingbang.myworld.news.kr36.properties.Kr36NewsDigestProperties;
import com.dingbang.myworld.news.kr36.service.NewsDigestDelivery;
import com.dingbang.myworld.news.kr36.service.NewsDigestService;
import com.dingbang.myworld.news.kr36.service.impl.NewsDigestServiceImpl;
import com.dingbang.myworld.news.kr36.task.DailyNewsDigestTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 36氪 RSS 解析、日报生成和每日去重推送。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
class Kr36NewsDigestFeatureTest {

    /**
     * 验证渠道拒绝消息后不保存成功日期，下一次执行仍会重试。
     *
     * @param directory 临时状态目录
     */
    @Test
    void doesNotMarkFailedDeliveryAsSent(@TempDir Path directory) {
        /**
         * 本次测试使用的发送状态配置。
         */
        Kr36NewsDigestProperties properties = new Kr36NewsDigestProperties();
        properties.setStateFile(directory.resolve("last-sent.txt"));
        /**
         * 固定的任务执行时间。
         */
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
        /**
         * 返回固定日报的生成服务。
         */
        NewsDigestService service = () -> new Kr36NewsDigestResp(LocalDate.of(2026, 10, 8), "日报", List.of());
        /**
         * 记录失败渠道的调用次数。
         */
        AtomicInteger attempts = new AtomicInteger();
        /**
         * 返回业务失败的推送渠道。
         */
        NewsDigestDelivery delivery = digest -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("飞书拒绝日报消息");
        };
        /**
         * 待验证的真实调度任务。
         */
        DailyNewsDigestTask task = new DailyNewsDigestTask(service, delivery, properties, clock);
        task.run();
        task.run();
        assertThat(Files.exists(properties.getStateFile())).isFalse();
        assertThat(attempts.get()).isEqualTo(2);
    }

    /**
     * 验证真实 36氪 RSS 日期和已有日期格式都能正确转换为 UTC 时间。
     *
     * @param publishedDate RSS 发布时间文本
     */
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-08 12:39:41  +0800", "Thu, 08 Oct 2026 12:39:41 +0800",
            "2026-10-08T04:39:41Z"})
    void parsesPublicationDates(String publishedDate) {
        /**
         * 使用真实 RSS 日期结构的新闻样本。
         */
        String xml = """
                <rss version="2.0"><channel><item>
                  <title>测试新闻</title><link>https://36kr.com/p/123</link>
                  <pubDate>%s</pubDate>
                </item></channel></rss>
                """.formatted(publishedDate);
        /**
         * 待验证的 RSS 读取器。
         */
        Kr36RssNewsMapper reader = new Kr36RssNewsMapper(new Kr36NewsDigestProperties());
        /**
         * 解析后的新闻列表。
         */
        List<Kr36NewsItemDTO> items = reader.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getPublishedAt()).isEqualTo(Instant.parse("2026-10-08T04:39:41Z"));
    }

    /**
     * 验证安全检测 HTML 页面会报告明确错误。
     */
    @Test
    void rejectsHtmlChallengePage() {
        /**
         * 返回 HTTP 200 时仍可能出现的 HTML 安全检测页面。
         */
        String html = "<!DOCTYPE html><html><body>正在进行安全检测</body></html>";
        /**
         * 待验证的 RSS 读取器。
         */
        Kr36RssNewsMapper reader = new Kr36RssNewsMapper(new Kr36NewsDigestProperties());
        assertThatThrownBy(() -> reader.parse(html.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTML 页面");
    }

    /**
     * 验证 XML 外部实体声明仍被拒绝。
     */
    @Test
    void rejectsExternalEntityDeclaration() {
        /**
         * 声明外部实体的 XML 样本。
         */
        String xml = """
                <!DOCTYPE rss [<!ENTITY external SYSTEM "file:///missing-rss-test-file">]>
                <rss><channel><title>&external;</title></channel></rss>
                """;
        /**
         * 待验证的 RSS 读取器。
         */
        Kr36RssNewsMapper reader = new Kr36RssNewsMapper(new Kr36NewsDigestProperties());
        assertThatThrownBy(() -> reader.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(org.xml.sax.SAXParseException.class);
    }

    /**
     * 验证 RSS 中的 HTML 摘要被清理且非 36氪链接被忽略。
     */
    @Test
    void parsesOfficialRssItems() {
        // 准备 RSS 测试样本。
        String xml = """
                <rss version="2.0"><channel>
                  <item><title>新产品发布</title><link>https://www.36kr.com/p/123</link>
                    <pubDate>Mon, 05 Oct 2026 08:00:00 +0800</pubDate>
                    <description><![CDATA[<p>发布&nbsp;详情</p>]]></description></item>
                  <item><title>外部内容</title><link>https://example.com/p/1</link>
                    <pubDate>Mon, 05 Oct 2026 08:00:00 +0800</pubDate></item>
                </channel></rss>
                """;
        // 创建待验证的 RSS 读取器。
        Kr36RssNewsMapper reader = new Kr36RssNewsMapper(new Kr36NewsDigestProperties());
        // 解析 RSS 新闻。
        List<Kr36NewsItemDTO> items = reader.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getDescription()).isEqualTo("发布 详情");
        assertThat(items.getFirst().getPublishedAt()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
    }

    /**
     * 验证只采用窗口内新闻、链接去重且附上原文索引。
     */
    @Test
    void summarizesRecentUniqueItems() {
        // 设置固定的生成时刻。
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
        // 创建待验证的 RSS 读取器。
        Kr36RssNewsMapper reader = new Kr36RssNewsMapper(new Kr36NewsDigestProperties()) {
            /**
             * 返回固定测试新闻。
             *
             * @return 固定新闻
             */
            @Override
            public List<Kr36NewsItemDTO> read() {
                return List.of(
                        new Kr36NewsItemDTO("新新闻", "https://36kr.com/p/1", "摘要", Instant.parse("2026-10-04T20:00:00Z")),
                        new Kr36NewsItemDTO("重复新闻", "https://36kr.com/p/1", "摘要", Instant.parse("2026-10-04T19:00:00Z")),
                        new Kr36NewsItemDTO("旧新闻", "https://36kr.com/p/2", "摘要", Instant.parse("2026-10-03T00:00:00Z")));
            }
        };
        // 创建 AI 对话替身。
        AiChatService ai = new AiChatService() {
            /**
             * 返回固定的新闻总结。
             *
             * @param request 对话请求
             * @return 固定响应
             */
            @Override
            public AiChatResponse chat(AiChatRequest request) {
                return new AiChatResponse("今日要点 [1]", "test");
            }

            /**
             * 禁止测试调用流式对话。
             *
             * @param request 对话请求
             * @return 不会返回
             */
            @Override
            public Flux<String> stream(AiChatRequest request) {
                throw new UnsupportedOperationException();
            }
        };
        // 创建日报配置。
        Kr36NewsDigestProperties properties = new Kr36NewsDigestProperties();
        // 生成日报。
        Kr36NewsDigestResp digest = new NewsDigestServiceImpl(reader, ai, properties, clock).generate();
        assertThat(digest.getArticles()).hasSize(1);
        assertThat(digest.getContent()).contains("今日要点 [1]", "https://36kr.com/p/1")
                .doesNotContain("https://36kr.com/p/2");
    }

    /**
     * 验证同一日期成功推送后不会再次调用推送渠道。
     *
     * @param directory 临时状态目录
     */
    @Test
    void sendsOnlyOncePerDate(@TempDir Path directory) {
        // 创建日报配置。
        Kr36NewsDigestProperties properties = new Kr36NewsDigestProperties();
        properties.setStateFile(directory.resolve("last-sent.txt"));
        // 设置固定的调度时刻。
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
        // 创建日报服务替身。
        NewsDigestService service = new NewsDigestServiceImpl(null, null, properties, clock) {
            /**
             * 返回固定的测试日报。
             *
             * @return 固定日报
             */
            @Override
            public Kr36NewsDigestResp generate() {
                return new Kr36NewsDigestResp(LocalDate.of(2026, 10, 5), "日报", List.of());
            }
        };
        // 记录推送次数。
        AtomicInteger deliveries = new AtomicInteger();
        // 创建推送渠道替身。
        NewsDigestDelivery delivery = digest -> deliveries.incrementAndGet();
        // 创建待验证的每日任务。
        DailyNewsDigestTask job = new DailyNewsDigestTask(service, delivery, properties, clock);
        job.run();
        job.run();
        assertThat(deliveries.get()).isEqualTo(1);
    }
}
