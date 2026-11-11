package com.creditflow.policy;

/** Registro de uma regra executada, com o motivo (persistido em credit_policy_result). */
public record RuleEvaluation(String ruleCode, Outcome outcome, String detail) {
}
