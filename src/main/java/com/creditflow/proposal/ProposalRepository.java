package com.creditflow.proposal;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findByStatusAndValidUntilBefore(ProposalStatus status, LocalDate date);

    List<Proposal> findTop50ByOrderByIdDesc();

    List<Proposal> findTop50ByCustomerIdOrderByIdDesc(Long customerId);
}
