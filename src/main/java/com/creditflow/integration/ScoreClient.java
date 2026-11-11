package com.creditflow.integration;

import java.util.Optional;

public interface ScoreClient {
    /** Score do cliente, ou vazio se o servico estiver indisponivel (timeout, erro, retries esgotados). */
    Optional<Integer> fetchScore(long customerId);
}
