package com.creditflow.policy;

import com.creditflow.integration.ScoreClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** Monta a cadeia: regras baratas primeiro, a chamada externa (score) por ultimo. */
@Component
public class PolicyEngine {
    private final PolicyHandler chain;
    private final String version;

    public PolicyEngine(PolicyProperties props, ScoreClient scoreClient) {
        MinIncomeRule minIncome = new MinIncomeRule(props.minIncome());
        IncomeCommitmentRule commitment = new IncomeCommitmentRule(props.maxCommitmentRatio());
        RelationshipRule relationship = new RelationshipRule(props.minRelationshipMonths());
        ScoreRule score = new ScoreRule(scoreClient, props.minScore());
        minIncome.linkWith(commitment).linkWith(relationship).linkWith(score);
        this.chain = minIncome;
        this.version = versionOf(List.of(minIncome, commitment, relationship, score), props);
    }

    public PolicyDecision evaluate(PolicyContext context) {
        return chain.handle(context);
    }

    /**
     * Versao da politica em vigor: hash das regras (e da ordem) e dos limites. Muda sozinha quando um limite ou uma
     * regra muda, entao cada decisao fica ligada exatamente a politica que a produziu.
     */
    public String version() {
        return version;
    }

    private static String versionOf(List<PolicyHandler> rules, PolicyProperties p) {
        StringBuilder sb = new StringBuilder();
        rules.forEach(r -> sb.append(r.code()).append('>'));
        sb.append(p.minIncome().stripTrailingZeros().toPlainString()).append('|')
                .append(p.maxCommitmentRatio().stripTrailingZeros().toPlainString()).append('|')
                .append(p.minRelationshipMonths()).append('|').append(p.minScore());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return "pol-" + HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
