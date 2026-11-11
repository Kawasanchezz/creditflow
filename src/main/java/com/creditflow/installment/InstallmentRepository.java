package com.creditflow.installment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findByContractIdOrderByNumberAsc(Long contractId);

    Optional<Installment> findByContractIdAndNumber(Long contractId, int number);
}
