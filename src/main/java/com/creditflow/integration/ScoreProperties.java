package com.creditflow.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("creditflow.score")
public record ScoreProperties(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
}
