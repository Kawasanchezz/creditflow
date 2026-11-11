package com.creditflow.proposal;

import static com.creditflow.proposal.ProposalStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditflow.installment.PrepaymentCalculator;
import com.creditflow.simulation.AmortizationSystem;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProposalStatusTest {

    @ParameterizedTest
    @EnumSource(ProposalStatus.class)
    void onlyDiagramTransitionsAreAllowed(ProposalStatus from) {
        Set<ProposalStatus> expected = switch (from) {
            case SIMULADA -> Set.of(EM_ANALISE);
            case EM_ANALISE -> Set.of(APROVADA, RECUSADA, ANALISE_MANUAL);
            case ANALISE_MANUAL -> Set.of(APROVADA, RECUSADA);
            case APROVADA -> Set.of(CONTRATADA, EXPIRADA);
            case CONTRATADA -> Set.of(ATIVA);
            default -> Set.of();
        };
        for (ProposalStatus to : ProposalStatus.values()) {
            assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isEqualTo(expected.contains(to));
        }
    }

    @Test
    void cannotContractARejectedProposal() {
        Proposal p = new Proposal(1L, BigDecimal.TEN, 1, BigDecimal.ONE, AmortizationSystem.PRICE,
                BigDecimal.TEN, BigDecimal.ZERO, 1);
        p.transitionTo(EM_ANALISE);
        p.transitionTo(RECUSADA);
        assertThatThrownBy(() -> p.transitionTo(CONTRATADA)).isInstanceOf(InvalidTransitionException.class);
        assertThat(p.getStatus()).isEqualTo(RECUSADA);
    }

    @Test
    void prepaymentDiscountsOnlyInterest() {
        BigDecimal amount = new BigDecimal("1000.00");
        BigDecimal principal = new BigDecimal("900.00");
        BigDecimal rate = new BigDecimal("0.02");
        assertThat(PrepaymentCalculator.payoffAmount(amount, principal, rate, 0)).isEqualByComparingTo("1000.00");
        assertThat(PrepaymentCalculator.payoffAmount(amount, principal, rate, 30)).isEqualByComparingTo("980.39");
        // desconto enorme nunca fica abaixo da amortizacao
        assertThat(PrepaymentCalculator.payoffAmount(amount, principal, new BigDecimal("5"), 3000))
                .isEqualByComparingTo("900.00");
    }
}
