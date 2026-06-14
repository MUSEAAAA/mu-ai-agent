package com.muse.muaiagent.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class DataSourceConfig {

    // ===== 云端 PostgreSQL（@Primary）=====
    @Primary
    @Bean(name = "cloudDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.cloud")
    public DataSourceProperties cloudDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "cloudDataSource")
    public HikariDataSource cloudDataSource(
            @Qualifier("cloudDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "cloudJdbcTemplate")
    public JdbcTemplate cloudJdbcTemplate(@Qualifier("cloudDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // ===== 本地 MySQL =====
    @Bean(name = "localDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.local")
    public DataSourceProperties localDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "localDataSource")
    public HikariDataSource localDataSource(
            @Qualifier("localDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "localJdbcTemplate")
    public JdbcTemplate localJdbcTemplate(@Qualifier("localDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}

