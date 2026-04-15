package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.config.TradeUpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BuffProperties.class, TradeUpProperties.class})
public class CsTaihuanApplication {
    public static void main(String[] args) {
        SpringApplication.run(CsTaihuanApplication.class, args);
    }
}

