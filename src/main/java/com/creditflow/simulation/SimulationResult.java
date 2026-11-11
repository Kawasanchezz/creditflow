package com.creditflow.simulation;

import java.math.BigDecimal;
import java.util.List;

public record SimulationResult(AmortizationSystem system, BigDecimal amount, int term, BigDecimal monthlyRate,
                               List<InstallmentLine> installments, BigDecimal totalPaid,
                               BigDecimal totalInterest, BigDecimal cetMonthly, BigDecimal cetAnnual) {
}
