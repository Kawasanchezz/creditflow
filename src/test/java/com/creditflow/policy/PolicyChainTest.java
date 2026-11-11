package com.creditflow.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditflow.integration.ScoreClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolicyChainTest {
    private static final PolicyProperties PROPS =
            new PolicyProperties(new BigDecimal("1000"), new BigDecimal("0.30"), 3, 500);

    private PolicyEngine engine(ScoreClient client) {
        return new PolicyEngine(PROPS, client);
    }

    private static PolicyContext ctx(String income, String commitments, String firstPayment, int months) {
        return new PolicyContext(1L, new BigDecimal(income), new BigDecimal(commitments),
                new BigDecimal(firstPayment), months);
    }

    // renda, compromissos, 1a parcela, meses, score (-1 = indisponivel), veredito, ultima regra executada
    @ParameterizedTest(name = "{index}: {5} em {6}")
    @CsvSource({
            "5000,0,500,12,700,APPROVED,SCORE",
            "999.99,0,100,12,700,REJECTED,MIN_INCOME",
            "1000,0,100,12,700,APPROVED,SCORE",
            "5000,1000,502,12,700,REJECTED,INCOME_COMMITMENT",
            "5000,1000,500,12,700,APPROVED,SCORE",
            "5000,1000,400,12,700,APPROVED,SCORE",
            "5000,0,500,2,700,REJECTED,RELATIONSHIP",
            "5000,0,500,3,700,APPROVED,SCORE",
            "5000,0,500,12,499,REJECTED,SCORE",
            "5000,0,500,12,500,APPROVED,SCORE",
            "5000,0,500,12,-1,MANUAL_REVIEW,SCORE"
    })
    void verdicts(String income, String commitments, String first, int months, int score,
                  Verdict verdict, String lastRule) {
        ScoreClient client = id -> score < 0 ? Optional.empty() : Optional.of(score);
        PolicyDecision d = engine(client).evaluate(ctx(income, commitments, first, months));
        assertThat(d.verdict()).isEqualTo(verdict);
        assertThat(d.trail().get(d.trail().size() - 1).ruleCode()).isEqualTo(lastRule);
        assertThat(d.reason()).isNotBlank();
    }

    @Test
    void chainStopsAtFirstRejectionAndNeverCallsScore() {
        AtomicInteger calls = new AtomicInteger();
        ScoreClient client = id -> {
            calls.incrementAndGet();
            return Optional.of(800);
        };
        PolicyDecision d = engine(client).evaluate(ctx("500", "0", "100", 12));
        assertThat(d.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(d.trail()).hasSize(1);
        assertThat(calls).hasValue(0);
    }

    @Test
    void approvedTrailRecordsEveryRuleInOrder() {
        PolicyDecision d = engine(id -> Optional.of(800)).evaluate(ctx("5000", "0", "500", 12));
        List<String> codes = d.trail().stream().map(RuleEvaluation::ruleCode).toList();
        assertThat(codes).containsExactly("MIN_INCOME", "INCOME_COMMITMENT", "RELATIONSHIP", "SCORE");
        assertThat(d.trail()).allMatch(e -> e.outcome() == Outcome.PASS);
    }

    @Test
    void zeroIncomeIsRejectedByCommitmentRuleWhenReachedDirectly() {
        IncomeCommitmentRule rule = new IncomeCommitmentRule(new BigDecimal("0.30"));
        PolicyDecision d = rule.handle(ctx("0", "0", "100", 12));
        assertThat(d.verdict()).isEqualTo(Verdict.REJECTED);
    }

    @Test
    void emptyDecisionHasBlankReason() {
        assertThat(new PolicyDecision(Verdict.APPROVED, List.of()).reason()).isEmpty();
    }
}
