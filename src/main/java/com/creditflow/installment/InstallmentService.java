package com.creditflow.installment;

import com.creditflow.common.BusinessRuleException;
import com.creditflow.common.NotFoundException;
import com.creditflow.contract.Contract;
import com.creditflow.contract.ContractRepository;
import com.creditflow.proposal.ProposalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallmentService {
    private final InstallmentRepository installments;
    private final ContractRepository contracts;
    private final ProposalRepository proposals;

    public InstallmentService(InstallmentRepository installments, ContractRepository contracts,
                              ProposalRepository proposals) {
        this.installments = installments;
        this.contracts = contracts;
        this.proposals = proposals;
    }

    @Transactional(readOnly = true)
    public List<Installment> list(Long contractId) {
        contracts.findById(contractId).orElseThrow(() -> new NotFoundException("Contrato nao encontrado"));
        return installments.findByContractIdOrderByNumberAsc(contractId);
    }

    @Transactional
    public Installment prepay(Long contractId, int number) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new NotFoundException("Contrato nao encontrado"));
        Installment inst = installments.findForUpdate(contractId, number)
                .orElseThrow(() -> new NotFoundException("Parcela nao encontrada"));
        if (inst.getStatus() != InstallmentStatus.PENDENTE) {
            throw new BusinessRuleException("Parcela ja quitada");
        }
        BigDecimal rate = proposals.findById(contract.getProposalId()).orElseThrow().getRate();
        long daysEarly = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), inst.getDueDate());
        inst.markPrepaid(PrepaymentCalculator.payoffAmount(inst.getAmount(), inst.getPrincipal(), rate, daysEarly));
        return inst;
    }
}
