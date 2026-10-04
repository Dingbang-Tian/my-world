package com.dingbang.myworld.agent.config;

import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.session.InMemorySessionRepository;
import com.dingbang.myworld.agent.session.SessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 根据配置装配内存模式或 MySQL 的 MyBatis-Plus 持久化服务。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentStorageProperties.class)
public class AgentStorageConfiguration {
    /**
     * 提供无需数据库的默认会话仓库。
     *
     * @return 内存会话仓库
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.agent.storage", name = "type", havingValue = "memory",
            matchIfMissing = true)
    public SessionRepository memorySessionRepository() {
        return new InMemorySessionRepository();
    }

    /**
     * 提供无需数据库的默认运行日志。
     *
     * @return 空运行日志
     */
    @Bean
    @ConditionalOnProperty(prefix = "my-world.agent.storage", name = "type", havingValue = "memory",
            matchIfMissing = true)
    public RunJournal memoryRunJournal() {
        return RunJournal.NONE;
    }

}
