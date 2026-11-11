package com.creditflow.proposal;

import java.util.Set;

/** Estados da proposta e transicoes validas (ver diagrama no README). */
public enum ProposalStatus {
    SIMULADA, EM_ANALISE, ANALISE_MANUAL, APROVADA, RECUSADA, CONTRATADA, ATIVA, EXPIRADA;

    public Set<ProposalStatus> allowedTargets() {
        return switch (this) {
            case SIMULADA -> Set.of(EM_ANALISE);
            case EM_ANALISE -> Set.of(APROVADA, RECUSADA, ANALISE_MANUAL);
            case ANALISE_MANUAL -> Set.of(APROVADA, RECUSADA);
            case APROVADA -> Set.of(CONTRATADA, EXPIRADA);
            case CONTRATADA -> Set.of(ATIVA);
            case ATIVA, RECUSADA, EXPIRADA -> Set.of();
        };
    }

    public boolean canTransitionTo(ProposalStatus target) {
        return allowedTargets().contains(target);
    }
}
