package com.creditflow.api;

import com.creditflow.contract.ContractResult;
import com.creditflow.installment.Installment;
import com.creditflow.installment.InstallmentStatus;
import com.creditflow.proposal.CreditPolicyResult;
import com.creditflow.proposal.Proposal;
import com.creditflow.proposal.ProposalHistory;
import com.creditflow.proposal.ProposalStatus;
import com.creditflow.policy.Outcome;
import com.creditflow.simulation.AmortizationSystem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record SimulationRequest(@NotNull AmortizationSystem system,
                                    @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                    @NotNull @DecimalMin("0.0") @DecimalMax("1") @Digits(integer = 1, fraction = 8) BigDecimal monthlyRate,
                                    @Min(1) @Max(480) int term) {
    }

    public record ProposalRequest(@NotNull @Positive Long customerId,
                                  @NotNull AmortizationSystem system,
                                  @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                  @NotNull @DecimalMin("0.0") @DecimalMax("1") @Digits(integer = 1, fraction = 8) BigDecimal monthlyRate,
                                  @Min(1) @Max(480) int term,
                                  @NotNull @DecimalMin("0.0") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal monthlyIncome,
                                  @NotNull @DecimalMin("0.0") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal existingCommitments,
                                  @Min(0) @Max(1200) int relationshipMonths) {
    }

    public record ManualDecisionRequest(boolean approved, @NotBlank @Size(max = 400) String reason) {
    }

    public record ProposalResponse(Long id, Long customerId, BigDecimal amount, int term, BigDecimal monthlyRate,
                                   AmortizationSystem system, ProposalStatus status, LocalDate validUntil) {
        public static ProposalResponse from(Proposal p) {
            return new ProposalResponse(p.getId(), p.getCustomerId(), p.getAmount(), p.getTerm(), p.getRate(),
                    p.getSystem(), p.getStatus(), p.getValidUntil());
        }
    }

    public record HistoryResponse(ProposalStatus from, ProposalStatus to, String reason, Instant at) {
        public static HistoryResponse from(ProposalHistory h) {
            return new HistoryResponse(h.getFromStatus(), h.getToStatus(), h.getReason(), h.getCreatedAt());
        }
    }

    public record PolicyResultResponse(String rule, Outcome outcome, String detail) {
        public static PolicyResultResponse from(CreditPolicyResult r) {
            return new PolicyResultResponse(r.getRuleCode(), r.getOutcome(), r.getDetail());
        }
    }

    public record InstallmentResponse(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest,
                                      BigDecimal amount, InstallmentStatus status, BigDecimal paidAmount) {
        public static InstallmentResponse from(Installment i) {
            return new InstallmentResponse(i.getNumber(), i.getDueDate(), i.getPrincipal(), i.getInterest(),
                    i.getAmount(), i.getStatus(), i.getPaidAmount());
        }
    }

    public record ContractResponse(Long id, Long proposalId, Instant signedAt, List<InstallmentResponse> installments) {
        public static ContractResponse from(ContractResult r) {
            return new ContractResponse(r.contract().getId(), r.contract().getProposalId(),
                    r.contract().getSignedAt(), r.installments().stream().map(InstallmentResponse::from).toList());
        }
    }
}
