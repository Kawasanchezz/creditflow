package com.creditflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthFailureThrottleTest {

    @Test
    void blockedOriginSurvivesTableFlooding() {
        AuthFailureThrottle throttle = new AuthFailureThrottle();
        for (int i = 0; i < AuthFailureThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("alvo");
        }
        assertThat(throttle.isBlocked("alvo")).isTrue();

        for (int i = 0; i < 25_000; i++) {
            throttle.recordFailure("origem-" + i);
        }

        assertThat(throttle.isBlocked("alvo")).isTrue();
    }

    @Test
    void belowLimitIsNotBlocked() {
        AuthFailureThrottle throttle = new AuthFailureThrottle();
        for (int i = 0; i < AuthFailureThrottle.MAX_FAILURES - 1; i++) {
            throttle.recordFailure("a");
        }
        assertThat(throttle.isBlocked("a")).isFalse();
    }
}
