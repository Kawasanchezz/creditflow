package com.creditflow.proposal;

import com.creditflow.common.BusinessRuleException;

public class InvalidTransitionException extends BusinessRuleException {
    public InvalidTransitionException(ProposalStatus from, ProposalStatus to) {
        super("Transicao invalida: " + from + " -> " + to);
    }
}
