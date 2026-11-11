package com.creditflow.common;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Politica de precisao do sistema (documentada no README):
 * valores monetarios usam scale = 2 e HALF_EVEN (arredondamento bancario);
 * calculos intermediarios usam MathContext de 34 digitos (DECIMAL128). Nunca double.
 */
public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    private Money() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }
}
