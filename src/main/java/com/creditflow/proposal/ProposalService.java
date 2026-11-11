package com.creditflow.proposal;

import com.creditflow.common.NotFoundException;
import com.creditflow.policy.Outcome;
import com.creditflow.policy.PolicyContext;
import com.creditflow.policy.PolicyDecision;
import com.creditflow.policy.PolicyEngine;
import com.creditflow.policy.RuleEvaluation;
import com.creditflow.policy.Verdict;
import com.creditflow.simulation.AmortizationSystem;
import com.creditflow.simulation.SimulationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProposalService {
    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final ProposalRepository proposals;
    private final ProposalHistoryRepository history;
    private final CreditPolicyResultRepository policyResults;
    private final SimulationService simulation;
    private final PolicyEngine policy;
    private final TransactionTemplate tx;
    private final int validityDays;

    public ProposalService(ProposalRepository proposals, ProposalHistoryRepository history,
                           CreditPolicyResultRepository policyResults, SimulationService simulation,
                           PolicyEngine policy, TransactionTemplate tx,
                           @Value("${creditflow.proposal.validity-days:7}") int validityDays) {
        this.proposals = proposals;
        this.history = history;
        this.policyResults = policyResults;
        this.simulation = simulation;
        this.policy = policy;
        this.tx = tx;
        this.validityDays = validityDays;
    }

    @Transactional
    public Proposal create(Long customerId, BigDecimal amount, int term, BigDecimal rate,
                           AmortizationSystem system, BigDecimal monthlyIncome,
                           BigDecimal existingCommitments, int relationshipMonths) {
        simulation.simulate(system, amount, rate, term); // valida os parametros
        Proposal p = proposals.save(new Proposal(customerId, amount, term, rate, system,
                monthlyIncome, existingCommitments, relationshipMonths));
        history.save(new ProposalHistory(p.getId(), null, ProposalStatus.SIMULADA, "Simulacao criada"));
        return p;
    }

    @Transactional(readOnly = true)
    public Proposal get(Long id) {
        return proposals.findById(id).orElseThrow(() -> new NotFoundException("Proposta " + id + " nao encontrada"));
    }

    @Transactional(readOnly = true)
    public List<ProposalHistory> history(Long id) {
        get(id);
        return history.findByProposalIdOrderByCreatedAtAscIdAsc(id);
    }

    @Transactional(readOnly = true)
    public List<CreditPolicyResult> policyResults(Long id) {
        get(id);
        return policyResults.findByProposalIdOrderByIdAsc(id);
    }

    /**
     * Envia a proposta: SIMULADA -> EM_ANALISE, executa a politica e decide.
     * A chamada ao score fica FORA de transacao para nao segurar conexao do banco durante timeouts/retries.
     */
    public Proposal submit(Long id) {
        PolicyContext ctx = tx.execute(status -> {
            Proposal p = get(id);
            move(p, ProposalStatus.EM_ANALISE, "Proposta enviada para analise");
            BigDecimal firstPayment = simulation.simulate(p.getSystem(), p.getAmount(), p.getRate(), p.getTerm())
                    .installments().get(0).payment();
            return new PolicyContext(p.getCustomerId(), p.getMonthlyIncome(), p.getExistingCommitments(),
                    firstPayment, p.getRelationshipMonths());
        });

        PolicyDecision decision = evaluateOrManualReview(ctx);

        return tx.execute(status -> {
            Proposal p = get(id);
            decision.trail().forEach(e -> policyResults.save(
                    new CreditPolicyResult(id, e.ruleCode(), e.outcome(), e.detail())));
            switch (decision.verdict()) {
                case APPROVED -> approve(p, decision.reason());
                case REJECTED -> move(p, ProposalStatus.RECUSADA, decision.reason());
                case MANUAL_REVIEW -> move(p, ProposalStatus.ANALISE_MANUAL, decision.reason());
            }
            return p;
        });
    }

    /** Falha inesperada na politica nao pode deixar a proposta presa em EM_ANALISE: vai para analise manual. */
    private PolicyDecision evaluateOrManualReview(PolicyContext ctx) {
        try {
            return policy.evaluate(ctx);
        } catch (RuntimeException e) {
            log.error("Policy evaluation failed for customer {}", ctx.customerId(), e);
            return new PolicyDecision(Verdict.MANUAL_REVIEW, List.of(new RuleEvaluation(
                    "POLICY_ERROR", Outcome.MANUAL, "Falha ao avaliar a politica; encaminhado para analise manual")));
        }
    }

    @Transactional
    public Proposal manualDecision(Long id, boolean approved, String reason) {
        Proposal p = get(id);
        if (approved) {
            approve(p, "Analise manual: " + reason);
        } else {
            move(p, ProposalStatus.RECUSADA, "Analise manual: " + reason);
        }
        return p;
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public int expireOverdue() {
        List<Proposal> overdue = proposals.findByStatusAndValidUntilBefore(ProposalStatus.APROVADA, LocalDate.now(ZoneOffset.UTC));
        overdue.forEach(p -> move(p, ProposalStatus.EXPIRADA, "Prazo de validade vencido"));
        return overdue.size();
    }

    /** Muda o status (validando a transicao) e grava o historico. Deve rodar dentro de transacao. */
    public void move(Proposal p, ProposalStatus target, String reason) {
        ProposalStatus from = p.getStatus();
        p.transitionTo(target);
        history.save(new ProposalHistory(p.getId(), from, target, reason));
    }

    private void approve(Proposal p, String reason) {
        move(p, ProposalStatus.APROVADA, reason);
        p.setValidUntil(LocalDate.now(ZoneOffset.UTC).plusDays(validityDays));
    }
}
