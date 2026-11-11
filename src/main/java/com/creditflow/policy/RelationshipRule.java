package com.creditflow.policy;

public class RelationshipRule extends PolicyHandler {
    private final int minMonths;

    public RelationshipRule(int minMonths) {
        this.minMonths = minMonths;
    }

    @Override
    public String code() {
        return "RELATIONSHIP";
    }

    @Override
    protected RuleResult evaluate(PolicyContext c) {
        String detail = "Relacionamento de " + c.relationshipMonths() + " meses (minimo " + minMonths + ")";
        return c.relationshipMonths() < minMonths ? RuleResult.reject(detail) : RuleResult.pass(detail);
    }
}
