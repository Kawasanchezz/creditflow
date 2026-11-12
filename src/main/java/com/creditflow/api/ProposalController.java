package com.creditflow.api;

import com.creditflow.api.ApiDtos.*;
import com.creditflow.contract.ContractResult;
import com.creditflow.common.NotFoundException;
import com.creditflow.contract.ContractService;
import com.creditflow.proposal.ProposalService;
import com.creditflow.security.AccessGuard;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {
    private final ProposalService proposals;
    private final ContractService contracts;
    private final AccessGuard guard;

    public ProposalController(ProposalService proposals, ContractService contracts, AccessGuard guard) {
        this.proposals = proposals;
        this.contracts = contracts;
        this.guard = guard;
    }

    @PostMapping
    public ResponseEntity<ProposalResponse> create(@Valid @RequestBody ProposalRequest r, Authentication auth) {
        guard.checkCustomer(auth, r.customerId());
        var p = proposals.create(r.customerId(), r.amount(), r.term(), r.monthlyRate(), r.system(),
                r.monthlyIncome(), r.existingCommitments(), r.relationshipMonths());
        return ResponseEntity.created(URI.create("/api/proposals/" + p.getId())).body(ProposalResponse.from(p));
    }

    @GetMapping
    public List<ProposalResponse> list(Authentication auth) {
        return proposals.list(guard.visibleCustomerId(auth)).stream().map(ProposalResponse::from).toList();
    }

    @GetMapping("/{id}/contract")
    public ContractResponse contractOf(@PathVariable Long id, Authentication auth) {
        guard.checkProposal(auth, id);
        return contracts.lookup(id).map(ContractResponse::from)
                .orElseThrow(() -> new NotFoundException("Proposta sem contrato"));
    }

    @GetMapping("/{id}")
    public ProposalResponse get(@PathVariable Long id, Authentication auth) {
        guard.checkProposal(auth, id);
        return ProposalResponse.from(proposals.get(id));
    }

    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@PathVariable Long id, Authentication auth) {
        guard.checkProposal(auth, id);
        return proposals.history(id).stream().map(HistoryResponse::from).toList();
    }

    @GetMapping("/{id}/policy-results")
    public List<PolicyResultResponse> policyResults(@PathVariable Long id, Authentication auth) {
        guard.checkProposal(auth, id);
        return proposals.policyResults(id).stream().map(PolicyResultResponse::from).toList();
    }

    @PostMapping("/{id}/submit")
    public ProposalResponse submit(@PathVariable Long id, Authentication auth) {
        guard.checkProposal(auth, id);
        return ProposalResponse.from(proposals.submit(id));
    }

    @PostMapping("/{id}/manual-decision")
    public ProposalResponse manualDecision(@PathVariable Long id, @Valid @RequestBody ManualDecisionRequest r) {
        return ProposalResponse.from(proposals.manualDecision(id, r.approved(), r.reason()));
    }

    /** Contratacao idempotente: repetir com a mesma Idempotency-Key devolve o mesmo contrato (200). */
    @PostMapping("/{id}/contract")
    public ResponseEntity<ContractResponse> contract(@PathVariable Long id,
                                                     @RequestHeader("Idempotency-Key") String key,
                                                     Authentication auth) {
        guard.checkProposal(auth, id);
        ContractResult result = contracts.contract(id, key);
        ContractResponse body = ContractResponse.from(result);
        return result.created()
                ? ResponseEntity.created(URI.create("/api/contracts/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }
}
