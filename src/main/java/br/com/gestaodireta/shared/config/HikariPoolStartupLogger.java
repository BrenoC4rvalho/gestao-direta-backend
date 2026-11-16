package br.com.gestaodireta.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class HikariPoolStartupLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariPoolStartupLogger.class);

    private final DataSource dataSource;

    public HikariPoolStartupLogger(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logEffectiveConfiguration() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            LOGGER.warn(
                    "HikariCP diagnostic unavailable because the configured datasource is {}",
                    dataSource.getClass().getName());
            return;
        }

        LOGGER.info("HikariCP - Pool name: {}", hikariDataSource.getPoolName());
        LOGGER.info("HikariCP - Minimum idle: {}", hikariDataSource.getMinimumIdle());
        LOGGER.info("HikariCP - Maximum pool size: {}", hikariDataSource.getMaximumPoolSize());
        LOGGER.info(
                "HikariCP - Connection timeout: {} ms", hikariDataSource.getConnectionTimeout());
        LOGGER.info("HikariCP - Idle timeout: {} ms", hikariDataSource.getIdleTimeout());
        LOGGER.info("HikariCP - Max lifetime: {} ms", hikariDataSource.getMaxLifetime());
    }
}
