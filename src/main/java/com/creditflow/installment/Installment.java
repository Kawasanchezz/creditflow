package com.creditflow.installment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "installment")
public class Installment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(nullable = false)
    private int number;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal principal;

    @Column(nullable = false)
    private BigDecimal interest;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentStatus status;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Installment() {
    }

    public Installment(Long contractId, int number, LocalDate dueDate, BigDecimal principal,
                       BigDecimal interest, BigDecimal amount) {
        this.contractId = contractId;
        this.number = number;
        this.dueDate = dueDate;
        this.principal = principal;
        this.interest = interest;
        this.amount = amount;
        this.status = InstallmentStatus.PENDENTE;
    }

    public void markPrepaid(BigDecimal paid) {
        this.status = InstallmentStatus.ANTECIPADA;
        this.paidAmount = paid;
        this.paidAt = Instant.now();
    }

    public Long getId() { return id; }
    public int getNumber() { return number; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getPrincipal() { return principal; }
    public BigDecimal getInterest() { return interest; }
    public BigDecimal getAmount() { return amount; }
    public InstallmentStatus getStatus() { return status; }
    public BigDecimal getPaidAmount() { return paidAmount; }
}
