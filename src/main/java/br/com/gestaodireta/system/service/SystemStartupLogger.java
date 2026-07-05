package br.com.gestaodireta.system.service;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SystemStartupLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemStartupLogger.class);

    private final Environment environment;

    public SystemStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupInformation() {
        LOGGER.info("Gestao Direta API started");
        LOGGER.info("Active profiles: {}", getActiveProfiles());
        LOGGER.info("Health endpoint: /api/actuator/health");
        LOGGER.info("System status endpoint: /api/system/status");
    }

    private String getActiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();

        if (activeProfiles.length == 0) {
            return "default";
        }

        return String.join(",", Arrays.asList(activeProfiles));
    }
}
