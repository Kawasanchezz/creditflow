package com.creditflow.proposal;

import com.creditflow.simulation.AmortizationSystem;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "proposal")
public class Proposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private int term;

    @Column(nullable = false)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmortizationSystem system;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(name = "monthly_income", nullable = false)
    private BigDecimal monthlyIncome;

    @Column(name = "existing_commitments", nullable = false)
    private BigDecimal existingCommitments;

    @Column(name = "relationship_months", nullable = false)
    private int relationshipMonths;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Version
    private long version;

    protected Proposal() {
    }

    public Proposal(Long customerId, BigDecimal amount, int term, BigDecimal rate, AmortizationSystem system,
                    BigDecimal monthlyIncome, BigDecimal existingCommitments, int relationshipMonths) {
        this.customerId = customerId;
        this.amount = amount;
        this.term = term;
        this.rate = rate;
        this.system = system;
        this.monthlyIncome = monthlyIncome;
        this.existingCommitments = existingCommitments;
        this.relationshipMonths = relationshipMonths;
        this.status = ProposalStatus.SIMULADA;
    }

    /** Unico ponto de mudanca de status: valida a transicao na maquina de estados. */
    public void transitionTo(ProposalStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(status, target);
        }
        this.status = target;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public int getTerm() { return term; }
    public BigDecimal getRate() { return rate; }
    public AmortizationSystem getSystem() { return system; }
    public ProposalStatus getStatus() { return status; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public BigDecimal getExistingCommitments() { return existingCommitments; }
    public int getRelationshipMonths() { return relationshipMonths; }
    public LocalDate getValidUntil() { return validUntil; }
    public long getVersion() { return version; }
}
