package com.dingbang.myworld.agent.runtime;

import com.dingbang.myworld.agent.config.AgentStorageConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 为真实 MySQL 集成测试启用 Spring Boot 自动配置。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AgentStorageConfiguration.class)
class MybatisMysqlPersistenceTestMysqlTestConfiguration { }
