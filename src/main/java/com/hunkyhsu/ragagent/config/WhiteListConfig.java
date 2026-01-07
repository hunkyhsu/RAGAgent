package com.hunkyhsu.ragagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "security")
@Data
public class WhiteListConfig {
    private List<String> whiteList;
}
