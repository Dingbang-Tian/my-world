package com.dingbang.myworld.agent.persistence.jdbc;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 提供经过版本化迁移的 Agent JDBC 数据库连接。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class AgentJdbcDatabase {
    /**
     * JDBC 连接地址。
     */
    private final String url;
    /**
     * 数据库用户名。
     */
    private final String username;
    /**
     * 数据库密码。
     */
    private final String password;

    /**
     * 创建数据库并执行待应用的迁移。
     *
     * @param url JDBC 地址；文件模式使用 jdbc:h2:file: 前缀
     * @param username 数据库用户名
     * @param password 数据库密码
     */
    public AgentJdbcDatabase(String url, String username, String password) {
        this.url = Objects.requireNonNull(url, "JDBC 地址不能为 null");
        this.username = Objects.requireNonNull(username, "数据库用户名不能为 null");
        this.password = Objects.requireNonNull(password, "数据库密码不能为 null");
        Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").load().migrate();
    }

    /**
     * 打开由调用方负责关闭的短生命周期连接。
     *
     * @return JDBC 连接
     * @throws SQLException 连接失败时
     */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
