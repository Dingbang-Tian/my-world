package com.dingbang.myworld.news.kr36.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 判断 36Kr 新闻日报 Webhook 是否已配置的 Spring 条件。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public class WebhookConfiguredCondition implements Condition {

    /**
     * 日报推送地址的配置项。
     */
    private static final String WEBHOOK_PROPERTY = "my-world.news.digest.webhook-url";

    /**
     * 检查 Webhook 地址是否已配置。
     *
     * @param context Spring 条件上下文
     * @param metadata 目标注解元数据
     * @return 是否存在非空地址
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty(WEBHOOK_PROPERTY));
    }
}
