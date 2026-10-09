package com.dingbang.myworld.news.kr36.task;

import com.dingbang.myworld.news.kr36.entity.resp.Kr36NewsDigestResp;
import com.dingbang.myworld.news.kr36.properties.Kr36NewsDigestProperties;
import com.dingbang.myworld.news.kr36.service.NewsDigestDelivery;
import com.dingbang.myworld.news.kr36.service.NewsDigestService;
import com.dingbang.myworld.news.kr36.config.WebhookConfiguredCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每天生成并推送一次 36氪新闻日报。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Component
@RequiredArgsConstructor
@Conditional(WebhookConfiguredCondition.class)
@Slf4j
public class DailyNewsDigestTask {

    /**
     * 日报生成服务。
     */
    private final NewsDigestService service;

    /**
     * 实际推送实现。
     */
    private final NewsDigestDelivery delivery;

    /**
     * 日报配置。
     */
    private final Kr36NewsDigestProperties properties;

    /**
     * 时间源。
     */
    private final Clock clock;

    /**
     * 到达配置的时间后执行日报；同一日期成功推送过则跳过。
     */
    @Scheduled(cron = "${my-world.news.digest.cron:0 0 8 * * *}",
            zone = "${my-world.news.digest.zone:Asia/Shanghai}")
    public synchronized void run() {
        // 计算当前调度日期。
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(properties.getZone())));
        try {
            if (today.equals(lastSentDate())) {
                log.info("跳过日报任务: {} 已有成功推送记录，状态文件 {}", today, properties.getStateFile().toAbsolutePath());
                return;
            }
            log.info("开始执行 36氪新闻日报任务: {}", today);
            // 生成本次日报。
            Kr36NewsDigestResp digest = service.generate();
            log.info("36氪日报生成完成: 新闻 {} 条，正文 {} 字符", digest.getArticles().size(), digest.getContent().length());
            delivery.deliver(digest);
            saveSentDate(today);
            log.info("36氪新闻日报已推送: {}, 新闻 {} 条", today, digest.getArticles().size());
        } catch (Exception exception) {
            log.error("36氪新闻日报生成或推送失败: {}", today, exception);
        }
    }

    /**
     * 读取最近一次成功推送的日期。
     *
     * @return 最近日期；没有记录时为空
     */
    private LocalDate lastSentDate() {
        // 获取状态文件。
        Path file = properties.getStateFile();
        if (!Files.exists(file)) return null;
        try {
            return LocalDate.parse(Files.readString(file, StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("无法读取新闻日报推送状态", exception);
        }
    }

    /**
     * 原子替换成功推送日期记录。
     *
     * @param date 成功推送日期
     */
    private void saveSentDate(LocalDate date) {
        // 获取状态文件的绝对路径。
        Path file = properties.getStateFile().toAbsolutePath();
        // 获取状态文件所在目录。
        Path parent = file.getParent();
        // 创建同目录中的临时文件。
        Path temporary = parent.resolve(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, date.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存新闻日报推送状态", exception);
        }
    }
}
