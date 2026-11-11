package com.creditflow.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI creditFlowApi() {
        return new OpenAPI().info(new Info()
                .title("CreditFlow API")
                .version("0.1.0")
                .description("Simulador e esteira de credito: simulacao Price/SAC, proposta, analise de politica, "
                        + "contratacao idempotente (header Idempotency-Key) e antecipacao de parcelas."));
    }
}
