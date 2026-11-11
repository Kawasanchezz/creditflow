package com.creditflow.api;

import com.creditflow.api.ApiDtos.*;
import com.creditflow.contract.ContractResult;
import com.creditflow.contract.ContractService;
import com.creditflow.proposal.ProposalService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {
    private final ProposalService proposals;
    private final ContractService contracts;

    public ProposalController(ProposalService proposals, ContractService contracts) {
        this.proposals = proposals;
        this.contracts = contracts;
    }

    @PostMapping
    public ResponseEntity<ProposalResponse> create(@Valid @RequestBody ProposalRequest r) {
        var p = proposals.create(r.customerId(), r.amount(), r.term(), r.monthlyRate(), r.system(),
                r.monthlyIncome(), r.existingCommitments(), r.relationshipMonths());
        return ResponseEntity.created(URI.create("/api/proposals/" + p.getId())).body(ProposalResponse.from(p));
    }

    @GetMapping("/{id}")
    public ProposalResponse get(@PathVariable Long id) {
        return ProposalResponse.from(proposals.get(id));
    }

    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@PathVariable Long id) {
        return proposals.history(id).stream().map(HistoryResponse::from).toList();
    }

    @GetMapping("/{id}/policy-results")
    public List<PolicyResultResponse> policyResults(@PathVariable Long id) {
        return proposals.policyResults(id).stream().map(PolicyResultResponse::from).toList();
    }

    @PostMapping("/{id}/submit")
    public ProposalResponse submit(@PathVariable Long id) {
        return ProposalResponse.from(proposals.submit(id));
    }

    @PostMapping("/{id}/manual-decision")
    public ProposalResponse manualDecision(@PathVariable Long id, @Valid @RequestBody ManualDecisionRequest r) {
        return ProposalResponse.from(proposals.manualDecision(id, r.approved(), r.reason()));
    }

    /** Contratacao idempotente: repetir com a mesma Idempotency-Key devolve o mesmo contrato (200). */
    @PostMapping("/{id}/contract")
    public ResponseEntity<ContractResponse> contract(@PathVariable Long id,
                                                     @RequestHeader("Idempotency-Key") String key) {
        ContractResult result = contracts.contract(id, key);
        ContractResponse body = ContractResponse.from(result);
        return result.created()
                ? ResponseEntity.created(URI.create("/api/contracts/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }
}
