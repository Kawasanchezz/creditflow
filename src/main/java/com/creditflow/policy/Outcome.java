package com.creditflow.policy;

/** Resultado de uma regra: aprova passando adiante, recusa, ou encaminha para analise manual. */
public enum Outcome {
    PASS, REJECT, MANUAL
}
