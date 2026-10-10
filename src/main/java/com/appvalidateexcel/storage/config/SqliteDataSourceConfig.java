package com.appvalidateexcel.storage.config;

import com.appvalidateexcel.storage.crypto.DbCipher;
import com.appvalidateexcel.storage.crypto.DbKey;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

@Configuration
public class SqliteDataSourceConfig {

    private final DataDirectoryResolver dataDirectoryResolver;

    public SqliteDataSourceConfig(DataDirectoryResolver dataDirectoryResolver) {
        this.dataDirectoryResolver = dataDirectoryResolver;
    }

    @Bean
    @Primary
    public RekeyableDataSource dataSource() {
        return new RekeyableDataSource(dataDirectoryResolver.getDatabaseFile());
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * DataSource bọc pool Hikari; khóa chính của DB (SQLCipher) lấy từ DbKey.
     */
    public static class RekeyableDataSource implements DataSource, DisposableBean {

        private final Path dbFile;
        private volatile HikariDataSource delegate;

        RekeyableDataSource(Path dbFile) {
            this.dbFile = dbFile;
            this.delegate = createPool(DbKey.get());
        }

        private HikariDataSource createPool(String key) {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl(DbCipher.jdbcUrl(dbFile, key));
            config.setPoolName("BqpSqlitePool");
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(60000);
            config.setMaxLifetime(300000);
            config.setConnectionInitSql(
                    "PRAGMA foreign_keys = ON; " +
                    "PRAGMA journal_mode = WAL; " +
                    "PRAGMA synchronous = NORMAL; " +
                    "PRAGMA busy_timeout = 5000;"
            );
            return new HikariDataSource(config);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public void destroy() {
            delegate.close();
        }
    }
}
