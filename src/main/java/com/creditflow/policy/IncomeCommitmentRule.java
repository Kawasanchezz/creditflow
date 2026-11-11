package com.creditflow.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Comprometimento de renda = (compromissos existentes + primeira parcela) / renda. */
public class IncomeCommitmentRule extends PolicyHandler {
    private final BigDecimal maxRatio;

    public IncomeCommitmentRule(BigDecimal maxRatio) {
        this.maxRatio = maxRatio;
    }

    @Override
    public String code() {
        return "INCOME_COMMITMENT";
    }

    @Override
    protected RuleResult evaluate(PolicyContext c) {
        if (c.monthlyIncome().signum() <= 0) {
            return RuleResult.reject("Renda invalida para calcular comprometimento");
        }
        BigDecimal ratio = c.existingCommitments().add(c.firstPayment())
                .divide(c.monthlyIncome(), 4, RoundingMode.HALF_EVEN);
        String detail = "Comprometimento " + ratio + " (limite " + maxRatio + ")";
        return ratio.compareTo(maxRatio) > 0 ? RuleResult.reject(detail) : RuleResult.pass(detail);
    }
}
