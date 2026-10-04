package com.dingbang.myworld.agent.config;

import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.agent.persistence.mybatis.AgentRunMapper;
import com.dingbang.myworld.agent.persistence.mybatis.AgentSessionMapper;
import com.dingbang.myworld.agent.persistence.mybatis.MybatisRunService;
import com.dingbang.myworld.agent.persistence.mybatis.MybatisSessionService;
import com.dingbang.myworld.agent.session.SessionRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;

/**
 * 仅在 MySQL 模式装配数据库 Mapper 和持久化服务。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "my-world.agent.storage", name = "type", havingValue = "mysql")
@MapperScan("com.dingbang.myworld.agent.persistence.mybatis")
public class AgentStorageConfigurationMysqlStorage {
    /**
     * 创建 Agent 专用的 MySQL 连接池。
     *
     * @param properties 存储配置
     * @return 数据源
     */
    @Bean
    public DataSource agentMysqlDataSource(AgentStorageProperties properties) {
        if (properties.getUrl() == null || !properties.getUrl().startsWith("jdbc:mysql:")
                || properties.getUsername() == null || properties.getUsername().isBlank()) {
            throw new IllegalArgumentException("MySQL 存储必须配置 jdbc:mysql: 地址和用户名");
        }
        // Agent MySQL 连接池。
        HikariDataSource source = new HikariDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setJdbcUrl(properties.getUrl());
        source.setUsername(properties.getUsername());
        source.setPassword(properties.getPassword());
        source.setMaximumPoolSize(10);
        source.setMinimumIdle(1);
        source.setConnectionInitSql("SET time_zone = '+00:00'");
        return source;
    }

    /**
     * 使用独立 MySQL 脚本验证并迁移 Agent 表。
     *
     * @param dataSource Agent 数据源
     * @return 已执行迁移的 Flyway
     */
    @Bean
    public Flyway agentMysqlFlyway(@Qualifier("agentMysqlDataSource") DataSource dataSource) {
        // MySQL 迁移器。
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/mysql").load();
        flyway.migrate();
        return flyway;
    }

    /**
     * 为 Service 层多表原子写入创建事务管理器。
     *
     * @param dataSource Agent 数据源
     * @return 事务管理器
     */
    @Bean
    public PlatformTransactionManager agentTransactionManager(
            @Qualifier("agentMysqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 提供无需代理自调用的短事务模板。
     *
     * @param manager 数据库事务管理器
     * @return 事务模板
     */
    @Bean
    public TransactionTemplate agentTransactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    /**
     * 创建 MyBatis-Plus 会话 Service。
     *
     * @param mapper 会话 Mapper
     * @param transactions 事务模板
     * @return 会话仓库
     */
    @Bean
    @DependsOn("agentMysqlFlyway")
    public SessionRepository mysqlSessionRepository(AgentSessionMapper mapper,
                                                    TransactionTemplate transactions) {
        return new MybatisSessionService(mapper, transactions);
    }

    /**
     * 创建 MyBatis-Plus 运行 Service 并扫描中断运行。
     *
     * @param runs 运行 Mapper
     * @param sessions 会话 Mapper
     * @param transactions 事务模板
     * @return 恢复日志服务
     */
    @Bean
    @DependsOn("agentMysqlFlyway")
    public RunJournal mysqlRunJournal(AgentRunMapper runs, AgentSessionMapper sessions,
                                      TransactionTemplate transactions) {
        // 可恢复的 MySQL 运行服务。
        MybatisRunService journal = new MybatisRunService(runs, sessions, transactions);
        journal.interruptOnStartup();
        return journal;
    }
}
