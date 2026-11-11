package com.creditflow.policy;

import java.math.BigDecimal;

public class MinIncomeRule extends PolicyHandler {
    private final BigDecimal minIncome;

    public MinIncomeRule(BigDecimal minIncome) {
        this.minIncome = minIncome;
    }

    @Override
    public String code() {
        return "MIN_INCOME";
    }

    @Override
    protected RuleResult evaluate(PolicyContext c) {
        if (c.monthlyIncome().compareTo(minIncome) < 0) {
            return RuleResult.reject("Renda " + c.monthlyIncome() + " abaixo do minimo " + minIncome);
        }
        return RuleResult.pass("Renda " + c.monthlyIncome() + " atende o minimo " + minIncome);
    }
}
