package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.config.BuffSessionProperties;
import com.qyaaaa.cstaihuan.config.TradeUpProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({BuffProperties.class, TradeUpProperties.class, BuffSessionProperties.class})
@EnableScheduling
@MapperScan("com.qyaaaa.cstaihuan.mapper")
public class CsTaihuanApplication {
    public static void main(String[] args) {
        SpringApplication.run(CsTaihuanApplication.class, args);
    }
}
