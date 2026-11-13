package com.creditflow.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Versao da politica por decisao e registro de desfechos (contestacao, chargeback, inadimplencia, quitacao). */
@SpringBootTest
@AutoConfigureMockMvc
class OutcomeIntegrationTest {

    @RegisterExtension
    static WireMockExtension score = WireMockExtension.newInstance().options(WireMockConfiguration.options().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("creditflow.score.base-url", score::baseUrl);
    }

    @Autowired MockMvc mvc;

    private static final RequestPostProcessor ANALYST = httpBasic("analyst", "test-analyst-pw");
    private static final RequestPostProcessor CUSTOMER_1 = httpBasic("customer-1", "test-customer-1-pw");
    private static final RequestPostProcessor CUSTOMER_2 = httpBasic("customer-2", "test-customer-2-pw");

    @BeforeEach
    void reset() {
        score.resetAll();
    }

    private long createProposal(long customerId) throws Exception {
        String body = "{\"customerId\":" + customerId + ",\"system\":\"PRICE\",\"amount\":3000,\"monthlyRate\":0.01,\"term\":6,"
                + "\"monthlyIncome\":9000,\"existingCommitments\":0,\"relationshipMonths\":24}";
        String res = mvc.perform(post("/api/proposals").with(ANALYST).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(res.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void stubScore(long customerId, int value) {
        score.stubFor(WireMock.get(urlPathEqualTo("/score/" + customerId)).willReturn(okJson("{\"score\":" + value + "}")));
    }

    private long approvedAndActive(long customerId) throws Exception {
        stubScore(customerId, 780);
        long id = createProposal(customerId);
        mvc.perform(post("/api/proposals/" + id + "/submit").with(ANALYST)).andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + id + "/contract").with(ANALYST).header("Idempotency-Key", "out-" + id))
                .andExpect(status().isCreated());
        return id;
    }

    private static String outcome(String type) {
        return "{\"type\":\"" + type + "\",\"detail\":\"teste\",\"occurredOn\":\"" + LocalDate.now(ZoneOffset.UTC) + "\"}";
    }

    @Test
    void everyPolicyResultCarriesThePolicyVersionThatProducedIt() throws Exception {
        long id = approvedAndActive(41);
        String body = mvc.perform(get("/api/proposals/" + id + "/policy-results").with(ANALYST))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"policyVersion\":\"pol-").doesNotContain("legacy");
    }

    @Test
    void defaultAfterApprovalAndContractIsLinkedToOriginalDecisionAndVersion() throws Exception {
        long id = approvedAndActive(42);
        mvc.perform(post("/api/proposals/" + id + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                        .content(outcome("INADIMPLENCIA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APROVADA"))
                .andExpect(jsonPath("$.signal").value("POSSIVEL_FALSO_POSITIVO"))
                .andExpect(jsonPath("$.policyVersion").value(org.hamcrest.Matchers.startsWith("pol-")))
                .andExpect(jsonPath("$.recordedBy").value("analyst"));
    }

    @Test
    void contestingARejectionIsAPossibleFalseNegative() throws Exception {
        stubScore(43, 300);
        long id = createProposal(43);
        mvc.perform(post("/api/proposals/" + id + "/submit").with(ANALYST)).andExpect(jsonPath("$.status").value("RECUSADA"));
        mvc.perform(post("/api/proposals/" + id + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                        .content(outcome("CONTESTACAO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("RECUSADA"))
                .andExpect(jsonPath("$.signal").value("POSSIVEL_FALSO_NEGATIVO"));
    }

    @Test
    void outcomesThatDoNotMakeSenseAreRefused() throws Exception {
        long undecided = createProposal(44);
        mvc.perform(post("/api/proposals/" + undecided + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content(outcome("CONTESTACAO"))).andExpect(status().isConflict());

        stubScore(45, 780);
        long approvedOnly = createProposal(45);
        mvc.perform(post("/api/proposals/" + approvedOnly + "/submit").with(ANALYST)).andExpect(status().isOk());
        mvc.perform(post("/api/proposals/" + approvedOnly + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content(outcome("CHARGEBACK"))).andExpect(status().isConflict());

        long active = approvedAndActive(46);
        String future = "{\"type\":\"QUITACAO\",\"occurredOn\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(3) + "\"}";
        mvc.perform(post("/api/proposals/" + active + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content(future)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/proposals/" + active + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"INVENTADO\",\"occurredOn\":\"2026-01-01\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAnalystsRecordOutcomesAndCustomersSeeOnlyTheirOwn() throws Exception {
        long id = approvedAndActive(1);
        mvc.perform(post("/api/proposals/" + id + "/outcomes").with(CUSTOMER_1).contentType(MediaType.APPLICATION_JSON)
                .content(outcome("QUITACAO"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/proposals/" + id + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content(outcome("QUITACAO"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.signal").value("DECISAO_CONFIRMADA"));
        mvc.perform(get("/api/proposals/" + id + "/outcomes").with(CUSTOMER_1)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("QUITACAO"));
        mvc.perform(get("/api/proposals/" + id + "/outcomes").with(CUSTOMER_2)).andExpect(status().isNotFound());
    }

    @Test
    void summaryCrossesPolicyVersionDecisionAndOutcomeForAnalystsOnly() throws Exception {
        long id = approvedAndActive(47);
        mvc.perform(post("/api/proposals/" + id + "/outcomes").with(ANALYST).contentType(MediaType.APPLICATION_JSON)
                .content(outcome("CHARGEBACK"))).andExpect(status().isCreated());
        String body = mvc.perform(get("/api/outcomes/summary").with(ANALYST)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"signal\":\"POSSIVEL_FALSO_POSITIVO\"").contains("\"policyVersion\":\"pol-")
                .contains("\"decision\":\"APROVADA\"").contains("\"count\":");
        mvc.perform(get("/api/outcomes/summary").with(CUSTOMER_1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/outcomes/summary")).andExpect(status().isUnauthorized());
    }
}
