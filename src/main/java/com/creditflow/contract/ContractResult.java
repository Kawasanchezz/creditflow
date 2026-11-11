package com.creditflow.contract;

import com.creditflow.installment.Installment;
import java.util.List;

/** created=false indica que o pedido repetido devolveu o contrato ja existente (idempotencia). */
public record ContractResult(Contract contract, List<Installment> installments, boolean created) {
}
