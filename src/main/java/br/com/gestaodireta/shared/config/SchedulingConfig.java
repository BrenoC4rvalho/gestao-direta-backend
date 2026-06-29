package br.com.gestaodireta.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    public static final String APPLICATION_ZONE_ID = "America/Sao_Paulo";

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(APPLICATION_ZONE_ID));
    }
}
