package com.creditflow.common;

/** Violacao de regra de negocio (HTTP 409). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
