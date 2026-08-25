package com.santander.accountbalance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class TimeZoneConfig {

    @Bean
    public ZoneId zoneIdSP() {
        return ZoneId.of("America/Sao_Paulo");
    }
}
