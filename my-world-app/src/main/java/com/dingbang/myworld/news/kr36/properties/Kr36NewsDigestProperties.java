package com.dingbang.myworld.news.kr36.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 36Kr 新闻日报的抓取、调度和推送配置。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Data
@Validated
@ConfigurationProperties(prefix = "my-world.news.digest")
public class Kr36NewsDigestProperties {

    /**
     * 官方 RSS 订阅地址。
     */
    @NotEmpty
    private List<String> feedUrls = List.of("https://www.36kr.com/feed-article", "https://www.36kr.com/feed-newsflash");

    /**
     * 每次纳入总结的新闻上限，取值范围为 1 至 50。
     */
    @Min(1)
    @Max(50)
    private int maxItems = 25;

    /**
     * 新闻回溯窗口。
     */
    @NotNull
    private Duration lookback = Duration.ofHours(24);

    /**
     * 单条新闻摘要的最大字符数，取值范围为 0 至 1000。
     */
    @Min(0)
    @Max(1000)
    private int maxDescriptionChars = 240;

    /**
     * 发给模型的最大字符数，取值范围为 1000 至 10000。
     */
    @Min(1000)
    @Max(10000)
    private int maxPromptChars = 9000;

    /**
     * 每日调度的 cron 表达式。
     */
    private String cron = "0 0 8 * * *";

    /**
     * 每日调度采用的时区。
     */
    private String zone = "Asia/Shanghai";

    /**
     * 可选的 JSON Webhook 推送地址。
     */
    private String webhookUrl;

    /**
     * 飞书机器人要求消息包含的关键词，未配置时不添加前缀。
     */
    private String feishuKeyword;

    /**
     * 成功推送日期的本地记录文件。
     */
    @NotNull
    private Path stateFile = Path.of("data", "36kr-digest-last-sent.txt");
}
