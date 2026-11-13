package com.creditflow.outcome;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** Desfecho imutavel (so insercao), ligado a proposta e, por ela, a decisao original e a versao da politica. */
@Entity
@Immutable
@Table(name = "decision_outcome")
public class DecisionOutcome {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private Long proposalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutcomeType type;

    @Column(updatable = false)
    private String detail;

    @Column(name = "occurred_on", nullable = false, updatable = false)
    private LocalDate occurredOn;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected DecisionOutcome() {
    }

    public DecisionOutcome(Long proposalId, OutcomeType type, String detail, LocalDate occurredOn, String recordedBy) {
        this.proposalId = proposalId;
        this.type = type;
        this.detail = detail;
        this.occurredOn = occurredOn;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public OutcomeType getType() { return type; }
    public String getDetail() { return detail; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}
