package com.creditflow.contract;

import com.creditflow.common.BusinessRuleException;
import com.creditflow.installment.Installment;
import com.creditflow.installment.InstallmentRepository;
import com.creditflow.proposal.Proposal;
import com.creditflow.proposal.ProposalService;
import com.creditflow.proposal.ProposalStatus;
import com.creditflow.simulation.InstallmentLine;
import com.creditflow.simulation.SimulationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Contratacao idempotente. Duas defesas:
 * 1) consulta previa por Idempotency-Key / proposta;
 * 2) UNIQUE(proposal_id) e UNIQUE(idempotency_key) no banco (e @Version na proposta) para corridas:
 *    se duas requisicoes concorrentes passam pela consulta, uma falha e re-le o contrato vencedor.
 */
@Service
public class ContractService {
    private static final int MAX_KEY_LENGTH = 100;

    private final ContractRepository contracts;
    private final InstallmentRepository installments;
    private final ProposalService proposalService;
    private final SimulationService simulation;
    private final TransactionTemplate tx;

    public ContractService(ContractRepository contracts, InstallmentRepository installments,
                           ProposalService proposalService, SimulationService simulation, TransactionTemplate tx) {
        this.contracts = contracts;
        this.installments = installments;
        this.proposalService = proposalService;
        this.simulation = simulation;
        this.tx = tx;
    }

    public ContractResult contract(Long proposalId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key deve ter de 1 a " + MAX_KEY_LENGTH + " caracteres");
        }
        Optional<ContractResult> existing = tx.execute(s -> find(proposalId, idempotencyKey));
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return tx.execute(s -> create(proposalId, idempotencyKey));
        } catch (DataAccessException race) {
            return tx.execute(s -> find(proposalId, idempotencyKey)).orElseThrow(() -> race);
        }
    }

    private Optional<ContractResult> find(Long proposalId, String key) {
        Optional<Contract> byKey = contracts.findByIdempotencyKey(key);
        if (byKey.isPresent()) {
            if (!byKey.get().getProposalId().equals(proposalId)) {
                throw new BusinessRuleException("Idempotency-Key ja usada em outra proposta");
            }
            return Optional.of(view(byKey.get(), false));
        }
        return contracts.findByProposalId(proposalId).map(c -> view(c, false));
    }

    private ContractResult create(Long proposalId, String key) {
        Proposal p = proposalService.get(proposalId);
        if (p.getStatus() == ProposalStatus.APROVADA && p.getValidUntil() != null
                && p.getValidUntil().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new BusinessRuleException("Proposta com validade vencida");
        }
        proposalService.move(p, ProposalStatus.CONTRATADA, "Aceite do cliente"); // valida APROVADA -> CONTRATADA
        Instant now = Instant.now();
        Contract contract = contracts.saveAndFlush(new Contract(proposalId, key, now));

        LocalDate signedDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        List<Installment> created = new ArrayList<>();
        for (InstallmentLine line : simulation.simulate(p.getSystem(), p.getAmount(), p.getRate(), p.getTerm())
                .installments()) {
            created.add(installments.save(new Installment(contract.getId(), line.number(),
                    signedDate.plusMonths(line.number()), line.principal(), line.interest(), line.payment())));
        }
        proposalService.move(p, ProposalStatus.ATIVA, "Liberacao e parcelas geradas");
        return new ContractResult(contract, created, true);
    }

    private ContractResult view(Contract c, boolean created) {
        return new ContractResult(c, installments.findByContractIdOrderByNumberAsc(c.getId()), created);
    }
}
