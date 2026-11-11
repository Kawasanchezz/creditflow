package com.creditflow.contract;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "contract")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false, unique = true)
    private Long proposalId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    protected Contract() {
    }

    public Contract(Long proposalId, String idempotencyKey, Instant signedAt) {
        this.proposalId = proposalId;
        this.idempotencyKey = idempotencyKey;
        this.signedAt = signedAt;
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getSignedAt() { return signedAt; }
}
