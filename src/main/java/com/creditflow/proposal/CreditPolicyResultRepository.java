package com.creditflow.proposal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditPolicyResultRepository extends JpaRepository<CreditPolicyResult, Long> {
    List<CreditPolicyResult> findByProposalIdOrderByIdAsc(Long proposalId);
}
