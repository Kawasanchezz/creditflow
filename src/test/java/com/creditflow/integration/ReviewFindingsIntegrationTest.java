package com.creditflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditflow.common.BusinessRuleException;
import com.creditflow.contract.ContractService;
import com.creditflow.installment.InstallmentService;
import com.creditflow.proposal.ProposalService;
import com.creditflow.proposal.ProposalStatus;
import com.creditflow.simulation.AmortizationSystem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/** Regressoes dos achados da revisao independente: decisao manual, reanalise, controle de caracteres e antecipacao. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFindingsIntegrationTest {
    private static final RequestPostProcessor ANALYST = httpBasic("analyst", "test-analyst-pw");

    @Autowired MockMvc mvc;
    @Autowired ProposalService proposals;
    @Autowired ContractService contracts;
    @Autowired InstallmentService installments;
    @Autowired TransactionTemplate tx;
    @MockitoBean ScoreClient score;

    @BeforeEach
    void stubScore() {
        when(score.fetchScore(anyLong())).thenReturn(Optional.of(800));
    }

    private long newProposal() {
        return proposals.create(1L, new BigDecimal("2000"), 6, new BigDecimal("0.01"), AmortizationSystem.PRICE,
                new BigDecimal("9000"), BigDecimal.ZERO, 24).getId();
    }

    @Test
    void manualDecisionWithoutTheApprovedFlagIsRejectedInsteadOfDefaultingToRefusal() throws Exception {
        long id = newProposal();
        for (String body : List.of("{\"reason\":\"ok\"}", "{\"aproved\":true,\"reason\":\"ok\"}", "{\"approved\":null,\"reason\":\"ok\"}")) {
            mvc.perform(post("/api/proposals/" + id + "/manual-decision").with(ANALYST)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(proposals.get(id).getStatus()).isEqualTo(ProposalStatus.SIMULADA);
    }

    @Test
    void manualDecisionOnlyAppliesToManualReviewAndAnInterruptedAnalysisCanBeReevaluated() throws Exception {
        long id = newProposal();
        tx.executeWithoutResult(s -> proposals.move(proposals.get(id), ProposalStatus.EM_ANALISE, "analise interrompida"));
        mvc.perform(post("/api/proposals/" + id + "/manual-decision").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"reason\":\"tentativa\"}")).andExpect(status().isConflict());
        assertThat(proposals.get(id).getStatus()).isEqualTo(ProposalStatus.EM_ANALISE);
        assertThat(proposals.submit(id).getStatus()).isEqualTo(ProposalStatus.APROVADA);
    }

    @Test
    void controlCharactersAreRefusedWith400() throws Exception {
        long id = newProposal();
        mvc.perform(post("/api/proposals/" + id + "/manual-decision").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"reason\":\"a\\u0000b\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/proposals/" + id + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CONTESTACAO\",\"detail\":\"a\\u0000b\",\"occurredOn\":\"2020-01-01\"}")).andExpect(status().isBadRequest());
        proposals.submit(id);
        mvc.perform(post("/api/proposals/" + id + "/contract").with(ANALYST).header("Idempotency-Key", "a\u0000b"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentPrepaymentsOfTheSameInstallmentSucceedOnlyOnce() throws Exception {
        long id = newProposal();
        proposals.submit(id);
        long contractId = contracts.contract(id, "prepay-" + id).contract().getId();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return (Object) installments.prepay(contractId, 1);
                } catch (BusinessRuleException e) {
                    return e;
                }
            }));
        }
        go.countDown();
        int ok = 0, refused = 0;
        for (Future<Object> f : futures) {
            if (f.get() instanceof BusinessRuleException) {
                refused++;
            } else {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(refused).isEqualTo(threads - 1);
    }
}
