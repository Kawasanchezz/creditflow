package com.creditflow.simulation;

import java.math.BigDecimal;

/** Uma linha do cronograma: payment = principal + interest; balance = saldo devedor apos o pagamento. */
public record InstallmentLine(int number, BigDecimal payment, BigDecimal principal,
                              BigDecimal interest, BigDecimal balance) {
}
