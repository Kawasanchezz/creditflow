package com.creditflow.policy;

public record RuleResult(Outcome outcome, String detail) {
    public static RuleResult pass(String detail) {
        return new RuleResult(Outcome.PASS, detail);
    }

    public static RuleResult reject(String detail) {
        return new RuleResult(Outcome.REJECT, detail);
    }

    public static RuleResult manual(String detail) {
        return new RuleResult(Outcome.MANUAL, detail);
    }
}
