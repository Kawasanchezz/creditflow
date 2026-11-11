package com.creditflow.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * Elo da cadeia de responsabilidade. Cada regra avalia o contexto:
 * PASS passa para o proximo elo; REJECT e MANUAL encerram a cadeia.
 * Se todos passam, a proposta e aprovada. Cada avaliacao e registrada com o motivo.
 */
public abstract class PolicyHandler {
    private PolicyHandler next;

    public abstract String code();

    protected abstract RuleResult evaluate(PolicyContext context);

    /** Encadeia o proximo elo e o devolve, permitindo a.linkWith(b).linkWith(c). */
    public PolicyHandler linkWith(PolicyHandler nextHandler) {
        this.next = nextHandler;
        return nextHandler;
    }

    public final PolicyDecision handle(PolicyContext context) {
        return handle(context, new ArrayList<>());
    }

    private PolicyDecision handle(PolicyContext context, List<RuleEvaluation> trail) {
        RuleResult result = evaluate(context);
        trail.add(new RuleEvaluation(code(), result.outcome(), result.detail()));
        return switch (result.outcome()) {
            case REJECT -> new PolicyDecision(Verdict.REJECTED, trail);
            case MANUAL -> new PolicyDecision(Verdict.MANUAL_REVIEW, trail);
            case PASS -> next == null ? new PolicyDecision(Verdict.APPROVED, trail) : next.handle(context, trail);
        };
    }
}
