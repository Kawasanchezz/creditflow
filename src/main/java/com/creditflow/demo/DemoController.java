package com.creditflow.demo;

import com.creditflow.security.SecurityProperties;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apenas com creditflow.demo.enabled=true: publica as contas de demonstracao para a tela de login oferecer
 * entrada com um clique. NUNCA habilitar fora de uma maquina local de avaliacao.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "creditflow.demo.enabled", havingValue = "true")
public class DemoController {
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    public record DemoAccount(String label, String username, String password) {
    }

    public record DemoInfo(boolean enabled, List<DemoAccount> accounts) {
    }

    private final SecurityProperties security;

    public DemoController(SecurityProperties security) {
        this.security = security;
    }

    @PostConstruct
    void warn() {
        log.warn("MODO DEMONSTRACAO ATIVO: as credenciais de demonstracao sao publicas. Use apenas localmente.");
    }

    @GetMapping
    public DemoInfo info() {
        List<DemoAccount> accounts = new ArrayList<>();
        String c1 = security.customerPasswordsOrEmpty().get(1L);
        String c3 = security.customerPasswordsOrEmpty().get(3L);
        if (c1 != null) accounts.add(new DemoAccount("Entrar como Cliente", "customer-1", c1));
        if (c3 != null) accounts.add(new DemoAccount("Cliente com score instável", "customer-3", c3));
        if (security.analystPassword() != null) accounts.add(new DemoAccount("Entrar como Analista", "analyst", security.analystPassword()));
        return new DemoInfo(true, accounts);
    }
}
