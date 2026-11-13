package com.creditflow.simulation;

import com.creditflow.common.InvalidInputException;
import java.math.BigDecimal;
import java.util.List;

/** Estrategia de amortizacao (Strategy pattern): Price e SAC. */
public interface AmortizationStrategy {

    /** Limite de prazo (40 anos): impede que um unico pedido consuma CPU/memoria sem controle. */
    int MAX_TERM = 480;

    AmortizationSystem system();

    /**
     * @param principal   valor financiado (> 0)
     * @param monthlyRate taxa mensal em fracao decimal, ex.: 0.0199 = 1,99% (>= 0)
     * @param term        numero de parcelas (> 0)
     */
    List<InstallmentLine> schedule(BigDecimal principal, BigDecimal monthlyRate, int term);

    static void validate(BigDecimal principal, BigDecimal monthlyRate, int term) {
        if (principal == null || principal.signum() <= 0) {
            throw new InvalidInputException("amount must be positive");
        }
        if (monthlyRate == null || monthlyRate.signum() < 0) {
            throw new InvalidInputException("rate must be >= 0");
        }
        if (term <= 0) {
            throw new InvalidInputException("term must be positive");
        }
        if (term > MAX_TERM) {
            throw new InvalidInputException("term must be at most " + MAX_TERM);
        }
        if (principal.compareTo(BigDecimal.valueOf(term, 2)) < 0) {
            throw new InvalidInputException("amount too small: at least 0.01 per installment");
        }
    }
}
