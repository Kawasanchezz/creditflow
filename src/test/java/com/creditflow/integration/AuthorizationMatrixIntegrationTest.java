package com.creditflow.integration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditflow.contract.ContractService;
import com.creditflow.proposal.ProposalService;
import com.creditflow.simulation.AmortizationSystem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


/**
 * Matriz de autorizacao: cada endpoint contra cada papel (anonimo, dono, outro cliente, analista), aplicada no servidor.
 * Cada celula usa propostas e contratos novos, entao nenhuma depende do resultado de outra.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixIntegrationTest {
    private static final String ANON = "anonimo", OWNER = "dono", OTHER = "outro", ANALYST = "analista";

    @Autowired MockMvc mvc;
    @Autowired ProposalService proposals;
    @Autowired ContractService contracts;
    @MockitoBean ScoreClient score;

    private long undecided;
    private long contractId;
    private long active;

    @BeforeEach
    void fixtures() {
        when(score.fetchScore(anyLong())).thenReturn(Optional.of(800));
        undecided = proposal();
        active = proposal();
        proposals.submit(active);
        contractId = contracts.contract(active, "matrix-" + active).contract().getId();
    }

    private long proposal() {
        return proposals.create(1L, new BigDecimal("2000"), 6, new BigDecimal("0.01"), AmortizationSystem.PRICE,
                new BigDecimal("9000"), BigDecimal.ZERO, 24).getId();
    }

    private record Cell(String name, HttpMethod method, String path, String body, int anon, int owner, int other, int analyst) {
        int expected(String role) {
            return switch (role) { case ANON -> anon; case OWNER -> owner; case OTHER -> other; default -> analyst; };
        }
    }

    static Stream<Arguments> cells() {
        String sim = "{\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":6}";
        String create = "{\"customerId\":1,\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":6,"
                + "\"monthlyIncome\":5000,\"existingCommitments\":0,\"relationshipMonths\":12}";
        String decision = "{\"approved\":true,\"reason\":\"x\"}";
        String outcome = "{\"type\":\"CONTESTACAO\",\"occurredOn\":\"2020-01-01\"}";
        List<Cell> cells = List.of(
                new Cell("GET /api/me", HttpMethod.GET, "/api/me", null, 401, 200, 200, 200),
                new Cell("POST /api/simulations", HttpMethod.POST, "/api/simulations", sim, 401, 200, 200, 200),
                new Cell("GET /api/proposals", HttpMethod.GET, "/api/proposals", null, 401, 200, 200, 200),
                new Cell("POST /api/proposals", HttpMethod.POST, "/api/proposals", create, 401, 201, 403, 201),
                new Cell("GET proposal", HttpMethod.GET, "/api/proposals/{u}", null, 401, 200, 404, 200),
                new Cell("GET history", HttpMethod.GET, "/api/proposals/{u}/history", null, 401, 200, 404, 200),
                new Cell("GET policy-results", HttpMethod.GET, "/api/proposals/{u}/policy-results", null, 401, 200, 404, 200),
                new Cell("GET contract (none)", HttpMethod.GET, "/api/proposals/{u}/contract", null, 401, 404, 404, 404),
                new Cell("GET contract (active)", HttpMethod.GET, "/api/proposals/{a}/contract", null, 401, 200, 404, 200),
                new Cell("GET outcomes", HttpMethod.GET, "/api/proposals/{u}/outcomes", null, 401, 200, 404, 200),
                new Cell("POST submit", HttpMethod.POST, "/api/proposals/{u}/submit", null, 401, 200, 404, 200),
                new Cell("POST manual-decision", HttpMethod.POST, "/api/proposals/{u}/manual-decision", decision, 401, 403, 403, 409),
                new Cell("POST contract", HttpMethod.POST, "/api/proposals/{u}/contract", null, 401, 409, 404, 409),
                new Cell("POST outcomes", HttpMethod.POST, "/api/proposals/{u}/outcomes", outcome, 401, 403, 403, 409),
                new Cell("GET outcomes/summary", HttpMethod.GET, "/api/outcomes/summary", null, 401, 403, 403, 200),
                new Cell("GET installments", HttpMethod.GET, "/api/contracts/{c}/installments", null, 401, 200, 404, 200),
                new Cell("POST prepay", HttpMethod.POST, "/api/contracts/{c}/installments/1/prepay", null, 401, 200, 404, 200),
                new Cell("GET /api/demo (off)", HttpMethod.GET, "/api/demo", null, 404, 404, 404, 404),
                new Cell("GET /index.html", HttpMethod.GET, "/index.html", null, 200, 200, 200, 200),
                new Cell("GET unknown api", HttpMethod.GET, "/api/inexistente", null, 401, 404, 404, 404),
                new Cell("GET non-api path", HttpMethod.GET, "/interno", null, 401, 403, 403, 403));
        return cells.stream().flatMap(c -> Stream.of(ANON, OWNER, OTHER, ANALYST).map(r -> Arguments.of(c, r)));
    }

    @ParameterizedTest(name = "{0} como {1}")
    @MethodSource("cells")
    void everyEndpointEnforcesItsRulePerRole(Cell cell, String role) throws Exception {
        String path = cell.path().replace("{u}", String.valueOf(undecided)).replace("{a}", String.valueOf(active))
                .replace("{c}", String.valueOf(contractId));
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(cell.method(), path);
        if (cell.body() != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(cell.body());
        }
        if (path.endsWith("/contract") && cell.method() == HttpMethod.POST) {
            req.header("Idempotency-Key", "matrix-key-" + undecided);
        }
        switch (role) {
            case OWNER -> req.with(httpBasic("customer-1", "test-customer-1-pw"));
            case OTHER -> req.with(httpBasic("customer-2", "test-customer-2-pw"));
            case ANALYST -> req.with(httpBasic("analyst", "test-analyst-pw"));
            default -> { }
        }
        mvc.perform(req).andExpect(status().is(cell.expected(role)));
    }
}
