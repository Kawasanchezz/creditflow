package com.creditflow.policy;

import java.math.BigDecimal;

/**
 * Dados da proposta necessarios a politica.
 *
 * @param firstPayment valor da primeira (maior, no SAC) parcela
 */
public record PolicyContext(long customerId, BigDecimal monthlyIncome, BigDecimal existingCommitments,
                            BigDecimal firstPayment, int relationshipMonths) {
}
