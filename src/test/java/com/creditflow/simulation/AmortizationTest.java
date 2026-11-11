package com.creditflow.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmortizationTest {
    private final PriceStrategy price = new PriceStrategy();
    private final SacStrategy sac = new SacStrategy();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // PV, taxa mensal, prazo, PMT esperada (conferida em planilha: =PGTO(taxa; n; -PV))
    @ParameterizedTest(name = "Price PV={0} i={1} n={2} -> PMT {3}")
    @CsvSource({
            "10000,0.01,12,888.49",
            "100000,0.01,360,1028.61",
            "5000,0.02,6,892.63",
            "1200,0,12,100.00",
            "20000,0.0199,48,650.71"
    })
    void priceFirstPayment(String pv, String i, int n, String expectedPmt) {
        List<InstallmentLine> lines = price.schedule(bd(pv), bd(i), n);
        assertThat(lines).hasSize(n);
        assertThat(lines.get(0).payment()).isEqualByComparingTo(expectedPmt);
        assertThat(lines.get(n / 2).payment()).isEqualByComparingTo(expectedPmt);
    }

    @Test
    void priceKnownTotals() {
        // 1000 a 2% em 3x: PMT = 346.75 ; juros: 20.00 / 13.65 / 6.93 ; ultima parcela absorve o residuo
        List<InstallmentLine> l = price.schedule(bd("1000"), bd("0.02"), 3);
        assertThat(l.get(0).interest()).isEqualByComparingTo("20.00");
        assertThat(l.get(0).principal()).isEqualByComparingTo("326.75");
        assertThat(l.get(1).interest()).isEqualByComparingTo("13.46");
        assertThat(l.get(2).balance()).isEqualByComparingTo("0.00");
        assertThat(l.get(2).principal()).isEqualByComparingTo(l.get(1).balance());
    }

    // PV, taxa, prazo, parcela 1, parcela n, juros totais = i * A * n(n+1)/2 para divisao exata
    @ParameterizedTest(name = "SAC PV={0} i={1} n={2}")
    @CsvSource({
            "12000,0.01,12,1120.00,1010.00,780.00",
            "1000,0.02,4,270.00,255.00,50.00",
            "6000,0.015,6,1090.00,1015.00,315.00"
    })
    void sacTable(String pv, String i, int n, String first, String last, String totalInterest) {
        List<InstallmentLine> lines = sac.schedule(bd(pv), bd(i), n);
        assertThat(lines.get(0).payment()).isEqualByComparingTo(first);
        assertThat(lines.get(n - 1).payment()).isEqualByComparingTo(last);
        BigDecimal interest = lines.stream().map(InstallmentLine::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(interest).isEqualByComparingTo(totalInterest);
        // parcelas decrescentes
        for (int k = 1; k < n; k++) {
            assertThat(lines.get(k).payment()).isLessThan(lines.get(k - 1).payment());
        }
    }

    @Test
    void sacResidueGoesToLastInstallment() {
        // 1000 / 3 = 333.33 -> ultima amortizacao 333.34
        List<InstallmentLine> l = sac.schedule(bd("1000"), bd("0.01"), 3);
        assertThat(l.get(0).principal()).isEqualByComparingTo("333.33");
        assertThat(l.get(2).principal()).isEqualByComparingTo("333.34");
        assertThat(l.get(2).balance()).isEqualByComparingTo("0.00");
    }

    /** Propriedade: soma das amortizacoes = valor financiado, saldo final = 0, tudo com scale 2. */
    @Test
    void propertySumOfPrincipalEqualsAmountAndBalanceEndsAtZero() {
        Random rnd = new Random(42);
        for (AmortizationStrategy strategy : Stream.of(price, sac).toList()) {
            for (int run = 0; run < 500; run++) {
                int n = 1 + rnd.nextInt(360);
                BigDecimal pv = BigDecimal.valueOf(n * 100L + rnd.nextInt(2_000_000), 2); // >= 1 real por parcela
                BigDecimal rate = BigDecimal.valueOf(rnd.nextInt(800), 4); // 0% a 7,99% a.m.

                List<InstallmentLine> lines = strategy.schedule(pv, rate, n);

                BigDecimal principalSum = lines.stream().map(InstallmentLine::principal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(principalSum).as("%s pv=%s i=%s n=%d", strategy.system(), pv, rate, n)
                        .isEqualByComparingTo(pv);
                assertThat(lines.get(n - 1).balance()).isEqualByComparingTo("0");
                for (InstallmentLine line : lines) {
                    assertThat(line.payment().scale()).isEqualTo(2);
                    assertThat(line.payment()).isEqualByComparingTo(line.principal().add(line.interest()));
                    assertThat(line.balance().signum()).isGreaterThanOrEqualTo(0);
                }
            }
        }
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> sac.schedule(bd("0.05"), bd("0.01"), 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.schedule(bd("0"), bd("0.01"), 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price.schedule(bd("100"), bd("-0.01"), 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sac.schedule(bd("100"), bd("0.01"), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sac.schedule(null, bd("0.01"), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sac.schedule(bd("100"), null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleInstallment() {
        List<InstallmentLine> l = price.schedule(bd("1000"), bd("0.05"), 1);
        assertThat(l).hasSize(1);
        assertThat(l.get(0).payment()).isEqualByComparingTo("1050.00");
    }
}
