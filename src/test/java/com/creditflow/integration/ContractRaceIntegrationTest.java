package com.creditflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.creditflow.contract.ContractResult;
import com.creditflow.contract.ContractService;
import com.creditflow.proposal.ProposalService;
import com.creditflow.proposal.ProposalStatus;
import com.creditflow.simulation.AmortizationSystem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Regressao: pedidos de contratacao simultaneos nunca falham e nunca geram mais de um contrato. */
@SpringBootTest
class ContractRaceIntegrationTest {
    private static final int THREADS = 12;
    private static final int ROUNDS = 15;

    @Autowired ContractService contracts;
    @Autowired ProposalService proposals;
    @MockitoBean ScoreClient score;

    private long approvedProposal() {
        when(score.fetchScore(anyLong())).thenReturn(Optional.of(800));
        long id = proposals.create(1L, new BigDecimal("2000"), 6, new BigDecimal("0.01"), AmortizationSystem.PRICE,
                new BigDecimal("9000"), BigDecimal.ZERO, 24).getId();
        assertThat(proposals.submit(id).getStatus()).isEqualTo(ProposalStatus.APROVADA);
        return id;
    }

    private List<Object> runConcurrently(List<Callable<ContractResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<ContractResult> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return (Object) task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private void assertSingleContract(List<Object> results) {
        assertThat(results).noneMatch(r -> r instanceof Exception);
        List<ContractResult> ok = results.stream().map(r -> (ContractResult) r).toList();
        assertThat(ok.stream().map(r -> r.contract().getId()).distinct()).hasSize(1);
        assertThat(ok.stream().filter(ContractResult::created)).hasSize(1);
    }

    @Test
    void sameKeyFromManyThreadsYieldsOneContractAndNoErrors() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long id = approvedProposal();
            String key = "race-same-" + id;
            List<Callable<ContractResult>> tasks = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                tasks.add(() -> contracts.contract(id, key));
            }
            assertSingleContract(runConcurrently(tasks));
        }
    }

    @Test
    void differentKeysForTheSameProposalYieldOneContractAndNoErrors() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long id = approvedProposal();
            List<Callable<ContractResult>> tasks = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                String key = "race-diff-" + id + "-" + t;
                tasks.add(() -> contracts.contract(id, key));
            }
            assertSingleContract(runConcurrently(tasks));
        }
    }
}
