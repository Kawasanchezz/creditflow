package com.creditflow.outcome;

import com.creditflow.common.BusinessRuleException;
import com.creditflow.proposal.CreditPolicyResult;
import com.creditflow.proposal.Proposal;
import com.creditflow.proposal.ProposalHistory;
import com.creditflow.proposal.ProposalService;
import com.creditflow.proposal.ProposalStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra o que aconteceu DEPOIS da decisao (contestacao, chargeback, inadimplencia, quitacao) e cruza com a
 * decisao original e a versao da politica. A contestacao do cliente e o sinal mais barato de falso positivo ou
 * falso negativo: nao exige uma equipe de revisao manual.
 */
@Service
public class OutcomeService {
    private static final Set<ProposalStatus> DECIDED = EnumSet.of(ProposalStatus.APROVADA, ProposalStatus.RECUSADA,
            ProposalStatus.CONTRATADA, ProposalStatus.ATIVA, ProposalStatus.EXPIRADA);

    public record OutcomeView(Long id, Long proposalId, OutcomeType type, String detail, LocalDate occurredOn,
                              Instant recordedAt, String recordedBy, String policyVersion, String decision,
                              String signal) {
    }

    public record SummaryRow(String policyVersion, String decision, OutcomeType type, String signal, long count) {
    }

    private record Context(String policyVersion, String decision) {
    }

    private record Key(String policyVersion, String decision, OutcomeType type, String signal) {
    }

    private final DecisionOutcomeRepository outcomes;
    private final ProposalService proposals;

    public OutcomeService(DecisionOutcomeRepository outcomes, ProposalService proposals) {
        this.outcomes = outcomes;
        this.proposals = proposals;
    }

    @Transactional
    public OutcomeView record(Long proposalId, OutcomeType type, String detail, LocalDate occurredOn, String recordedBy) {
        Proposal p = proposals.get(proposalId);
        if (type == OutcomeType.CONTESTACAO) {
            if (!DECIDED.contains(p.getStatus())) {
                throw new BusinessRuleException("Proposta ainda sem decisao: nao ha o que contestar");
            }
        } else if (p.getStatus() != ProposalStatus.ATIVA) {
            throw new BusinessRuleException("Desfecho " + type + " exige proposta ATIVA (com contrato)");
        }
        if (occurredOn.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("occurredOn nao pode estar no futuro");
        }
        DecisionOutcome saved = outcomes.save(new DecisionOutcome(proposalId, type, detail, occurredOn, recordedBy));
        return view(saved, contextOf(proposalId));
    }

    @Transactional(readOnly = true)
    public List<OutcomeView> list(Long proposalId) {
        proposals.get(proposalId);
        Context ctx = contextOf(proposalId);
        return outcomes.findByProposalIdOrderByRecordedAtAscIdAsc(proposalId).stream().map(o -> view(o, ctx)).toList();
    }

    /** Quantos desfechos por versao da politica, decisao original e sinal. */
    @Transactional(readOnly = true)
    public List<SummaryRow> summary() {
        Map<Long, Context> cache = new HashMap<>();
        Map<Key, Long> counts = new LinkedHashMap<>();
        for (DecisionOutcome o : outcomes.findTop5000ByOrderByIdDesc()) {
            Context ctx = cache.computeIfAbsent(o.getProposalId(), this::contextOf);
            counts.merge(new Key(ctx.policyVersion(), ctx.decision(), o.getType(), signal(ctx.decision(), o.getType())), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new SummaryRow(e.getKey().policyVersion(), e.getKey().decision(), e.getKey().type(),
                        e.getKey().signal(), e.getValue()))
                .sorted(Comparator.comparing(SummaryRow::policyVersion).thenComparing(SummaryRow::decision)
                        .thenComparing(r -> r.type().name()))
                .toList();
    }

    static String signal(String decision, OutcomeType type) {
        if ("APROVADA".equals(decision) && (type == OutcomeType.CHARGEBACK || type == OutcomeType.INADIMPLENCIA)) {
            return "POSSIVEL_FALSO_POSITIVO";
        }
        if ("RECUSADA".equals(decision) && type == OutcomeType.CONTESTACAO) {
            return "POSSIVEL_FALSO_NEGATIVO";
        }
        if ("APROVADA".equals(decision) && type == OutcomeType.QUITACAO) {
            return "DECISAO_CONFIRMADA";
        }
        return "INCONCLUSIVO";
    }

    private Context contextOf(Long proposalId) {
        List<ProposalHistory> history = proposals.history(proposalId);
        String decision = history.stream().anyMatch(h -> h.getToStatus() == ProposalStatus.APROVADA) ? "APROVADA"
                : history.stream().anyMatch(h -> h.getToStatus() == ProposalStatus.RECUSADA) ? "RECUSADA" : "SEM_DECISAO";
        List<CreditPolicyResult> results = proposals.policyResults(proposalId);
        return new Context(results.isEmpty() ? "n/a" : results.get(0).getPolicyVersion(), decision);
    }

    private static OutcomeView view(DecisionOutcome o, Context ctx) {
        return new OutcomeView(o.getId(), o.getProposalId(), o.getType(), o.getDetail(), o.getOccurredOn(),
                o.getRecordedAt(), o.getRecordedBy(), ctx.policyVersion(), ctx.decision(), signal(ctx.decision(), o.getType()));
    }
}
