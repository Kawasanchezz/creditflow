package com.creditflow.proposal;

import com.creditflow.policy.Outcome;
import jakarta.persistence.*;

@Entity
@Table(name = "credit_policy_result")
public class CreditPolicyResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    private String detail;

    protected CreditPolicyResult() {
    }

    public CreditPolicyResult(Long proposalId, String ruleCode, Outcome outcome, String detail) {
        this.proposalId = proposalId;
        this.ruleCode = ruleCode;
        this.outcome = outcome;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public String getRuleCode() { return ruleCode; }
    public Outcome getOutcome() { return outcome; }
    public String getDetail() { return detail; }
}
