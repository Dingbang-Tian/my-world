package com.dingbang.myworld.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 选择内存或 MySQL 数据库的 Agent 存储配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ConfigurationProperties(prefix = "my-world.agent.storage")
@Getter
@Setter
public class AgentStorageProperties {
    /**
     * 存储类型，取 memory 或 mysql。
     */
    private String type = "memory";
    /**
     * MySQL JDBC 地址。
     */
    private String url;
    /**
     * 数据库用户名。
     */
    private String username;
    /**
     * 数据库密码。
     */
    private String password = "";

}
