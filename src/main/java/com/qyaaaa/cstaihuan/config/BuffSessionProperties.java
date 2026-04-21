package com.qyaaaa.cstaihuan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buff.session")
@Data
public class BuffSessionProperties {
    private String storagePath = "data/buff-session.json";
}
