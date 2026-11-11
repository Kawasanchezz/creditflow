package com.creditflow.simulation;

import com.creditflow.common.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * SAC (amortizacao constante): A = PV / n; P_k = A + (PV - (k-1) * A) * i.
 * A amortizacao e arredondada; a ultima absorve o residuo para o saldo zerar.
 */
@Component
public class SacStrategy implements AmortizationStrategy {

    @Override
    public AmortizationSystem system() {
        return AmortizationSystem.SAC;
    }

    @Override
    public List<InstallmentLine> schedule(BigDecimal principal, BigDecimal monthlyRate, int term) {
        AmortizationStrategy.validate(principal, monthlyRate, term);
        BigDecimal constant = Money.round(principal.divide(BigDecimal.valueOf(term), Money.MC));
        List<InstallmentLine> lines = new ArrayList<>(term);
        BigDecimal balance = Money.round(principal);
        for (int k = 1; k <= term; k++) {
            BigDecimal interest = Money.round(balance.multiply(monthlyRate, Money.MC));
            BigDecimal amortization = (k == term) ? balance : constant.min(balance);
            balance = balance.subtract(amortization);
            lines.add(new InstallmentLine(k, amortization.add(interest), amortization, interest, balance));
        }
        return lines;
    }
}
