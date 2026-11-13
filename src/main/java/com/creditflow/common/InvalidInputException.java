package com.creditflow.common;

/** Entrada invalida de dominio (HTTP 400). A mensagem e escrita por nos e e segura para devolver ao cliente. */
public class InvalidInputException extends IllegalArgumentException {
    public InvalidInputException(String message) {
        super(message);
    }
}
