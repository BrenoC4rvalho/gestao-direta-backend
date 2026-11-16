package br.com.gestaodireta.shared.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfiguration {

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties dataSourceProperties, Environment environment) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dataSourceProperties.determineUrl());
        hikariConfig.setUsername(dataSourceProperties.determineUsername());
        hikariConfig.setPassword(dataSourceProperties.determinePassword());

        String driverClassName = dataSourceProperties.determineDriverClassName();
        if (StringUtils.hasText(driverClassName)) {
            hikariConfig.setDriverClassName(driverClassName);
        }

        hikariConfig.setPoolName(
                environment.getProperty("spring.datasource.hikari.pool-name", "GestaoDiretaPool"));
        hikariConfig.setMinimumIdle(
                environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, 2));
        hikariConfig.setMaximumPoolSize(
                environment.getProperty(
                        "spring.datasource.hikari.maximum-pool-size", Integer.class, 10));
        hikariConfig.setConnectionTimeout(
                environment.getProperty(
                        "spring.datasource.hikari.connection-timeout", Long.class, 30000L));
        hikariConfig.setIdleTimeout(
                environment.getProperty(
                        "spring.datasource.hikari.idle-timeout", Long.class, 600000L));
        hikariConfig.setMaxLifetime(
                environment.getProperty(
                        "spring.datasource.hikari.max-lifetime", Long.class, 1800000L));

        return new HikariDataSource(hikariConfig);
    }
}
