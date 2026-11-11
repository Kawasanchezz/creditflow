package com.creditflow.proposal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalHistoryRepository extends JpaRepository<ProposalHistory, Long> {
    List<ProposalHistory> findByProposalIdOrderByCreatedAtAscIdAsc(Long proposalId);
}
