package com.creditflow.policy;

import java.util.List;

public record PolicyDecision(Verdict verdict, List<RuleEvaluation> trail) {

    /** Motivo final: o detalhe da ultima regra executada (a que decidiu, ou a ultima que passou). */
    public String reason() {
        return trail.isEmpty() ? "" : trail.get(trail.size() - 1).detail();
    }
}
