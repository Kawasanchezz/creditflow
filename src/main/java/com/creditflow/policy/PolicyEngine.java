package com.creditflow.policy;

import com.creditflow.integration.ScoreClient;
import org.springframework.stereotype.Component;

/** Monta a cadeia: regras baratas primeiro, a chamada externa (score) por ultimo. */
@Component
public class PolicyEngine {
    private final PolicyHandler chain;

    public PolicyEngine(PolicyProperties props, ScoreClient scoreClient) {
        PolicyHandler head = new MinIncomeRule(props.minIncome());
        head.linkWith(new IncomeCommitmentRule(props.maxCommitmentRatio()))
                .linkWith(new RelationshipRule(props.minRelationshipMonths()))
                .linkWith(new ScoreRule(scoreClient, props.minScore()));
        this.chain = head;
    }

    public PolicyDecision evaluate(PolicyContext context) {
        return chain.handle(context);
    }
}
