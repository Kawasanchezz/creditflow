package com.creditflow.proposal;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Historico imutavel: so insercao, nunca update/delete (@Immutable + sem setters). */
@Entity
@Immutable
@Table(name = "proposal_history")
public class ProposalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private Long proposalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private ProposalStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private ProposalStatus toStatus;

    @Column(updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProposalHistory() {
    }

    public ProposalHistory(Long proposalId, ProposalStatus fromStatus, ProposalStatus toStatus, String reason) {
        this.proposalId = proposalId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public ProposalStatus getFromStatus() { return fromStatus; }
    public ProposalStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
