package com.creditflow.contract;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByIdempotencyKey(String idempotencyKey);

    Optional<Contract> findByProposalId(Long proposalId);
}
