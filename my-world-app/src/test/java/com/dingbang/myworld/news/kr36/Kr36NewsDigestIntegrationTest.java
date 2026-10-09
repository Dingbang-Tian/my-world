package com.dingbang.myworld.news.kr36;

import com.dingbang.myworld.news.kr36.task.DailyNewsDigestTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 使用真实 RSS、AI 和 Webhook 配置验证 36Kr 日报任务。
 *
 * @author Sebastian
 * @since 2026/10/08
 */
@SpringBootTest
class Kr36NewsDigestIntegrationTest {

    /**
     * Spring 容器中的日报定时任务。
     */
    @Autowired
    private DailyNewsDigestTask dailyNewsDigestTask;

    /**
     * 使用应用启动的环境配置执行一次真实日报任务。
     */
    @Test
    void runsDigestWithRealEnvironment() {
        // 调用实际任务，使用当前环境的抓取、AI、推送和发送状态配置。
        dailyNewsDigestTask.run();
    }
}
