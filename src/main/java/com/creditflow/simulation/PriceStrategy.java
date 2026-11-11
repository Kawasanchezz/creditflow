package com.creditflow.simulation;

import com.creditflow.common.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Tabela Price (parcelas fixas): PMT = PV * i * (1+i)^n / ((1+i)^n - 1),
 * forma algebricamente igual a PV * i / (1 - (1+i)^-n), mas sem expoente negativo.
 * A ultima parcela absorve o residuo de arredondamento para o saldo zerar.
 */
@Component
public class PriceStrategy implements AmortizationStrategy {

    @Override
    public AmortizationSystem system() {
        return AmortizationSystem.PRICE;
    }

    @Override
    public List<InstallmentLine> schedule(BigDecimal principal, BigDecimal monthlyRate, int term) {
        AmortizationStrategy.validate(principal, monthlyRate, term);
        BigDecimal pmt = payment(principal, monthlyRate, term);
        List<InstallmentLine> lines = new ArrayList<>(term);
        BigDecimal balance = Money.round(principal);
        for (int k = 1; k <= term; k++) {
            BigDecimal interest = Money.round(balance.multiply(monthlyRate, Money.MC));
            BigDecimal amortization;
            BigDecimal paid;
            BigDecimal regular = pmt.subtract(interest).max(BigDecimal.ZERO);
            if (k == term || regular.compareTo(balance) >= 0) {
                amortization = balance; // ultima parcela (ou saldo ja coberto): absorve o residuo
                paid = amortization.add(interest);
            } else {
                paid = pmt.max(interest);
                amortization = regular;
            }
            balance = balance.subtract(amortization);
            lines.add(new InstallmentLine(k, paid, amortization, interest, balance));
        }
        return lines;
    }

    /** Parcela fixa teorica arredondada para 2 casas. */
    public static BigDecimal payment(BigDecimal principal, BigDecimal i, int n) {
        if (i.signum() == 0) {
            return Money.round(principal.divide(BigDecimal.valueOf(n), Money.MC));
        }
        BigDecimal growth = BigDecimal.ONE.add(i).pow(n, Money.MC);
        BigDecimal pmt = principal.multiply(i, Money.MC).multiply(growth, Money.MC)
                .divide(growth.subtract(BigDecimal.ONE), Money.MC);
        return Money.round(pmt);
    }
}
