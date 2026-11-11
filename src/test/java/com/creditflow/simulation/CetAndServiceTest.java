package com.creditflow.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CetAndServiceTest {
    private final SimulationService service = new SimulationService(List.of(new PriceStrategy(), new SacStrategy()));

    @ParameterizedTest
    @EnumSource(AmortizationSystem.class)
    void withoutFeesCetEqualsContractRate(AmortizationSystem system) {
        SimulationResult r = service.simulate(system, new BigDecimal("10000"), new BigDecimal("0.0199"), 24);
        assertThat(r.cetMonthly()).isCloseTo(new BigDecimal("0.0199"), within(new BigDecimal("0.0001")));
        assertThat(r.cetAnnual()).isCloseTo(new BigDecimal("0.2661"), within(new BigDecimal("0.001"))); // 1,0199^12-1
    }

    @Test
    void totalsAreConsistent() {
        SimulationResult r = service.simulate(AmortizationSystem.SAC, new BigDecimal("12000"), new BigDecimal("0.01"), 12);
        assertThat(r.totalInterest()).isEqualByComparingTo("780.00");
        assertThat(r.totalPaid()).isEqualByComparingTo("12780.00");
        assertThat(r.installments()).hasSize(12);
    }

    @Test
    void zeroRateGivesZeroCet() {
        SimulationResult r = service.simulate(AmortizationSystem.PRICE, new BigDecimal("1200"), BigDecimal.ZERO, 12);
        assertThat(r.cetMonthly()).isEqualByComparingTo("0");
        assertThat(r.totalInterest()).isEqualByComparingTo("0");
    }

    @Test
    void unsupportedSystemFails() {
        SimulationService empty = new SimulationService(List.of(new PriceStrategy()));
        assertThatThrownBy(() -> empty.simulate(AmortizationSystem.SAC, BigDecimal.TEN, BigDecimal.ONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
