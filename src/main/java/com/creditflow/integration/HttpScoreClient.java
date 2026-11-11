package com.creditflow.integration;

import io.github.resilience4j.retry.annotation.Retry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente do servico de score: timeout de conexao/leitura, retry limitado (Resilience4j)
 * e fallback que devolve vazio => a politica envia a proposta para analise manual.
 */
@Component
public class HttpScoreClient implements ScoreClient {
    private static final Logger log = LoggerFactory.getLogger(HttpScoreClient.class);

    private final RestClient restClient;

    public HttpScoreClient(ScoreProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @Override
    @Retry(name = "score", fallbackMethod = "unavailable")
    public Optional<Integer> fetchScore(long customerId) {
        ScoreResponse response = restClient.get().uri("/score/{id}", customerId)
                .retrieve().body(ScoreResponse.class);
        if (response == null || response.score() == null) {
            throw new IllegalStateException("empty score response");
        }
        return Optional.of(response.score());
    }

    /** Fallback do Resilience4j (invocado por reflexao). */
    public Optional<Integer> unavailable(long customerId, Throwable cause) {
        log.warn("Score service unavailable for customer {}: {}", customerId, cause.toString());
        return Optional.empty();
    }

    record ScoreResponse(Integer score) {
    }
}
