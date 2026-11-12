package com.creditflow.security;

import com.creditflow.common.NotFoundException;
import com.creditflow.contract.ContractRepository;
import com.creditflow.proposal.ProposalService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Verificacao de propriedade no servidor (evita IDOR). Para clientes, recurso de outra pessoa
 * responde 404, igual a um recurso inexistente, para nao revelar quais ids existem.
 */
@Component
public class AccessGuard {
    private final ProposalService proposals;
    private final ContractRepository contracts;

    public AccessGuard(ProposalService proposals, ContractRepository contracts) {
        this.proposals = proposals;
        this.contracts = contracts;
    }

    public void checkCustomer(Authentication auth, Long customerId) {
        if (!isAnalyst(auth) && !customerId.equals(customerIdOf(auth))) {
            throw new AccessDeniedException("Cliente nao autorizado");
        }
    }

    public void checkProposal(Authentication auth, Long proposalId) {
        Long owner = proposals.get(proposalId).getCustomerId();
        if (!isAnalyst(auth) && !owner.equals(customerIdOf(auth))) {
            throw new NotFoundException("Proposta " + proposalId + " nao encontrada");
        }
    }

    public void checkContract(Authentication auth, Long contractId) {
        Long proposalId = contracts.findById(contractId)
                .orElseThrow(() -> new NotFoundException("Contrato nao encontrado")).getProposalId();
        try {
            checkProposal(auth, proposalId);
        } catch (NotFoundException e) {
            throw new NotFoundException("Contrato nao encontrado");
        }
    }

    /** Id do cliente cujas propostas a pessoa pode listar; nulo para analistas (todas). */
    public Long visibleCustomerId(Authentication auth) {
        return isAnalyst(auth) ? null : customerIdOf(auth);
    }

    private static boolean isAnalyst(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ANALYST"));
    }

    private static Long customerIdOf(Authentication auth) {
        return auth.getPrincipal() instanceof CreditFlowUser user ? user.getCustomerId() : null;
    }
}
