package com.creditflow.simulation;

import com.creditflow.common.InvalidInputException;
import com.creditflow.common.Money;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {
    private final Map<AmortizationSystem, AmortizationStrategy> strategies = new EnumMap<>(AmortizationSystem.class);

    public SimulationService(List<AmortizationStrategy> available) {
        available.forEach(s -> strategies.put(s.system(), s));
    }

    public SimulationResult simulate(AmortizationSystem system, BigDecimal amount, BigDecimal monthlyRate, int term) {
        AmortizationStrategy strategy = strategies.get(system);
        if (strategy == null) {
            throw new InvalidInputException("unsupported system: " + system);
        }
        List<InstallmentLine> lines = strategy.schedule(amount, monthlyRate, term);
        BigDecimal totalPaid = lines.stream().map(InstallmentLine::payment).reduce(Money.zero(), BigDecimal::add);
        BigDecimal totalInterest = lines.stream().map(InstallmentLine::interest).reduce(Money.zero(), BigDecimal::add);
        BigDecimal cetMonthly = CetCalculator.monthlyIrr(Money.round(amount),
                lines.stream().map(InstallmentLine::payment).toList());
        return new SimulationResult(system, Money.round(amount), term, monthlyRate, lines, totalPaid,
                totalInterest, cetMonthly, CetCalculator.annualFromMonthly(cetMonthly));
    }
}
