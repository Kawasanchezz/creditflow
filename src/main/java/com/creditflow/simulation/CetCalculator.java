package com.creditflow.simulation;

import com.creditflow.common.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * CET aproximado: taxa interna de retorno (TIR) do fluxo valor liberado x parcelas pagas.
 * NAO inclui tarifas nem IOF, logo e apenas uma aproximacao do CET regulamentado pelo Banco Central.
 */
public final class CetCalculator {
    private static final int ITERATIONS = 100;
    private static final BigDecimal MAX_MONTHLY_RATE = BigDecimal.valueOf(10);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private CetCalculator() {
    }

    /** TIR mensal por bisseccao (NPV e decrescente na taxa). */
    public static BigDecimal monthlyIrr(BigDecimal released, List<BigDecimal> payments) {
        if (npv(BigDecimal.ZERO, released, payments).signum() <= 0) {
            return BigDecimal.ZERO.setScale(8);
        }
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal high = MAX_MONTHLY_RATE;
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal mid = low.add(high).divide(TWO, Money.MC);
            if (npv(mid, released, payments).signum() > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low.add(high).divide(TWO, Money.MC).setScale(8, RoundingMode.HALF_EVEN);
    }

    /** Converte taxa mensal em anual composta: (1+i)^12 - 1. */
    public static BigDecimal annualFromMonthly(BigDecimal monthly) {
        return BigDecimal.ONE.add(monthly).pow(12, Money.MC).subtract(BigDecimal.ONE)
                .setScale(8, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal npv(BigDecimal rate, BigDecimal released, List<BigDecimal> payments) {
        BigDecimal base = BigDecimal.ONE.add(rate);
        BigDecimal factor = BigDecimal.ONE;
        BigDecimal total = released.negate();
        for (BigDecimal payment : payments) {
            factor = factor.multiply(base, Money.MC);
            total = total.add(payment.divide(factor, Money.MC), Money.MC);
        }
        return total;
    }
}
