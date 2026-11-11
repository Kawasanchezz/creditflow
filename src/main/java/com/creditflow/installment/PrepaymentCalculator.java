package com.creditflow.installment;

import com.creditflow.common.Money;
import java.math.BigDecimal;

/**
 * Antecipacao: desconta os juros embutidos pelo tempo antecipado, com desconto simples
 * pro rata: valor = parcela / (1 + i * dias/30). Nunca abaixo da amortizacao da parcela
 * (o desconto incide apenas sobre os juros). Aproximacao de estudo.
 */
public final class PrepaymentCalculator {
    private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);

    private PrepaymentCalculator() {
    }

    public static BigDecimal payoffAmount(BigDecimal amount, BigDecimal principal,
                                          BigDecimal monthlyRate, long daysEarly) {
        if (daysEarly <= 0) {
            return amount;
        }
        BigDecimal factor = BigDecimal.ONE.add(
                monthlyRate.multiply(BigDecimal.valueOf(daysEarly), Money.MC).divide(DAYS_IN_MONTH, Money.MC));
        BigDecimal discounted = Money.round(amount.divide(factor, Money.MC));
        return discounted.max(principal);
    }
}
