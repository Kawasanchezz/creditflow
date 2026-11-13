package com.creditflow.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditflow.integration.ScoreClient;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyVersionTest {
    private static final ScoreClient CLIENT = id -> Optional.of(700);

    private static String versionOf(String income, String ratio, int months, int score) {
        return new PolicyEngine(new PolicyProperties(new BigDecimal(income), new BigDecimal(ratio), months, score), CLIENT).version();
    }

    @Test
    void versionIsStableAndWellFormed() {
        String v = versionOf("1000", "0.30", 3, 500);
        assertThat(v).matches("pol-[0-9a-f]{8}");
        assertThat(versionOf("1000", "0.30", 3, 500)).isEqualTo(v);
    }

    @Test
    void numericallyEqualThresholdsGiveTheSameVersion() {
        assertThat(versionOf("1000.00", "0.3", 3, 500)).isEqualTo(versionOf("1000", "0.30", 3, 500));
    }

    @Test
    void anyChangedThresholdProducesADifferentVersion() {
        List<String> versions = List.of(
                versionOf("1000", "0.30", 3, 500),
                versionOf("1500", "0.30", 3, 500),
                versionOf("1000", "0.35", 3, 500),
                versionOf("1000", "0.30", 6, 500),
                versionOf("1000", "0.30", 3, 600));
        Set<String> distinct = new HashSet<>(versions);
        assertThat(distinct).hasSize(versions.size());
    }
}
