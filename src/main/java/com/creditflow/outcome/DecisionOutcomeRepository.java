package com.creditflow.outcome;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionOutcomeRepository extends JpaRepository<DecisionOutcome, Long> {
    List<DecisionOutcome> findByProposalIdOrderByRecordedAtAscIdAsc(Long proposalId);

    List<DecisionOutcome> findTop1000ByOrderByIdDesc();
}
