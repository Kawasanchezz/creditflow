package com.creditflow.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Contas em memoria (projeto academico). Em producao a identidade viria de um provedor (OIDC).
 * customerPasswords: id do cliente -> senha da conta "customer-{id}".
 */
@ConfigurationProperties("creditflow.security")
public record SecurityProperties(String analystPassword, Map<Long, String> customerPasswords) {

    public Map<Long, String> customerPasswordsOrEmpty() {
        return customerPasswords == null ? Map.of() : customerPasswords;
    }
}
