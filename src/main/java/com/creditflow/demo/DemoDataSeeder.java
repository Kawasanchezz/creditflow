package com.creditflow.demo;

import com.creditflow.contract.ContractService;
import com.creditflow.proposal.ProposalService;
import com.creditflow.simulation.AmortizationSystem;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Cria propostas de exemplo (aprovada, contratada, recusada, analise manual) so quando o banco esta vazio. */
@Component
@ConditionalOnProperty(name = "creditflow.demo.seed-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final BigDecimal RATE = new BigDecimal("0.0199");

    private final ProposalService proposals;
    private final ContractService contracts;

    public DemoDataSeeder(ProposalService proposals, ContractService contracts) {
        this.proposals = proposals;
        this.contracts = contracts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!proposals.list(null).isEmpty()) {
            return;
        }
        seed(1, "10000", 12, AmortizationSystem.PRICE, true, true);
        seed(1, "25000", 36, AmortizationSystem.SAC, true, false);
        seed(2, "15000", 24, AmortizationSystem.PRICE, true, false);
        seed(3, "8000", 12, AmortizationSystem.PRICE, true, false);
        seed(4, "5000", 6, AmortizationSystem.SAC, true, false);
        seed(1, "3000", 6, AmortizationSystem.PRICE, false, false);
        log.info("Dados de demonstracao criados.");
    }

    private void seed(long customer, String amount, int term, AmortizationSystem system, boolean submit, boolean contract) {
        try {
            long id = proposals.create(customer, new BigDecimal(amount), term, RATE, system,
                    new BigDecimal("8000"), BigDecimal.ZERO, 24).getId();
            if (submit) {
                proposals.submit(id);
            }
            if (contract) {
                contracts.contract(id, "demo-seed-" + id);
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao criar dado de demonstracao para o cliente {}: {}", customer, e.toString());
        }
    }
}
