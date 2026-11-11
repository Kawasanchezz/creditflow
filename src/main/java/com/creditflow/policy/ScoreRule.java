package com.creditflow.policy;

import com.creditflow.integration.ScoreClient;
import java.util.Optional;

/** Regra de score. Servico indisponivel => analise manual (fallback), nunca recusa nem aprovacao as cegas. */
public class ScoreRule extends PolicyHandler {
    private final ScoreClient scoreClient;
    private final int minScore;

    public ScoreRule(ScoreClient scoreClient, int minScore) {
        this.scoreClient = scoreClient;
        this.minScore = minScore;
    }

    @Override
    public String code() {
        return "SCORE";
    }

    @Override
    protected RuleResult evaluate(PolicyContext c) {
        Optional<Integer> score = scoreClient.fetchScore(c.customerId());
        if (score.isEmpty()) {
            return RuleResult.manual("Servico de score indisponivel; encaminhado para analise manual");
        }
        String detail = "Score " + score.get() + " (minimo " + minScore + ")";
        return score.get() < minScore ? RuleResult.reject(detail) : RuleResult.pass(detail);
    }
}
