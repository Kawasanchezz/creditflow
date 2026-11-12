package com.creditflow.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** enabled: expoe as contas de demonstracao em GET /api/demo; seedData: cria propostas de exemplo na primeira subida. */
@ConfigurationProperties("creditflow.demo")
public record DemoProperties(boolean enabled, boolean seedData) {
}
