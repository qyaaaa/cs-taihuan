package com.qyaaaa.cstaihuan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, BuffProperties buffProperties) {
        Duration timeout = Duration.ofMillis(buffProperties.getTimeoutMillis());
        return builder
            .setConnectTimeout(timeout)
            .setReadTimeout(timeout)
            .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
