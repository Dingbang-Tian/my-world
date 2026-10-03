package com.dingbang.myworld.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 选择内存或 MySQL 数据库的 Agent 存储配置。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
@ConfigurationProperties(prefix = "my-world.agent.storage")
public class AgentStorageProperties {
    /** 存储类型，取 memory 或 mysql。 */
    private String type = "memory";
    /** MySQL JDBC 地址。 */
    private String url;
    /** 数据库用户名。 */
    private String username;
    /** 数据库密码。 */
    private String password = "";

    /** @return 存储类型 */
    public String getType() { return type; }
    /** @param type 存储类型 */
    public void setType(String type) { this.type = type; }
    /** @return JDBC 地址 */
    public String getUrl() { return url; }
    /** @param url JDBC 地址 */
    public void setUrl(String url) { this.url = url; }
    /** @return 数据库用户名 */
    public String getUsername() { return username; }
    /** @param username 数据库用户名 */
    public void setUsername(String username) { this.username = username; }
    /** @return 数据库密码 */
    public String getPassword() { return password; }
    /** @param password 数据库密码 */
    public void setPassword(String password) { this.password = password; }
}
