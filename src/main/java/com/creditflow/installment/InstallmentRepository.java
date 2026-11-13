package com.creditflow.installment;

import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findByContractIdOrderByNumberAsc(Long contractId);

    Optional<Installment> findByContractIdAndNumber(Long contractId, int number);

    /** Trava a linha para que duas antecipacoes simultaneas da mesma parcela nao se sobreponham. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Installment i where i.contractId = :contractId and i.number = :number")
    Optional<Installment> findForUpdate(@Param("contractId") Long contractId, @Param("number") int number);
}
