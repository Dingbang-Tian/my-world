package com.dingbang.myworld.news.kr36.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingbang.myworld.news.kr36.mapper.Kr36RssNewsMapper;
import com.dingbang.myworld.news.kr36.properties.Kr36NewsDigestProperties;
import com.dingbang.myworld.news.kr36.service.NewsDigestDelivery;
import com.dingbang.myworld.news.kr36.service.impl.WebhookNewsDigestDelivery;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * 36氪新闻日报的抓取、总结和定时推送装配。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(Kr36NewsDigestProperties.class)
public class Kr36NewsConfiguration {

    /**
     * 创建官方 RSS 读取器。
     *
     * @param properties 日报配置
     * @return RSS 读取器
     */
    @Bean
    public Kr36RssNewsMapper kr36RssNewsMapper(Kr36NewsDigestProperties properties) {
        return new Kr36RssNewsMapper(properties);
    }

    /**
     * 创建日报使用的系统时钟。
     *
     * @return 系统 UTC 时钟
     */
    @Bean
    public Clock kr36NewsClock() {
        return Clock.systemUTC();
    }

    /**
     * 在配置了 Webhook 地址时创建通用推送实现。
     *
     * @param properties 日报配置
     * @param mapper JSON 编码器
     * @return Webhook 推送器
     */
    @Bean
    @Conditional(WebhookConfiguredCondition.class)
    public NewsDigestDelivery webhookNewsDigestDelivery(Kr36NewsDigestProperties properties, ObjectMapper mapper) {
        return new WebhookNewsDigestDelivery(properties, mapper);
    }

}
