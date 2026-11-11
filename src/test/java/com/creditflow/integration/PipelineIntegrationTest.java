package com.creditflow.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditflow.contract.ContractRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Fluxo completo com banco (H2 modo PostgreSQL + Flyway) e WireMock no lugar do servico de score. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ANALYST")
class PipelineIntegrationTest {

    @RegisterExtension
    static WireMockExtension score = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("creditflow.score.base-url", score::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ContractRepository contracts;

    @BeforeEach
    void reset() {
        score.resetAll();
    }

    private static com.github.tomakehurst.wiremock.core.WireMockConfiguration wireMockConfig() {
        return com.github.tomakehurst.wiremock.core.WireMockConfiguration.options();
    }

    private long createProposal(long customerId, String income, int months) throws Exception {
        String body = """
                {"customerId":%d,"system":"PRICE","amount":10000,"monthlyRate":0.0199,"term":12,
                 "monthlyIncome":%s,"existingCommitments":0,"relationshipMonths":%d}""".formatted(customerId, income, months);
        String res = mvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private String submit(long id) throws Exception {
        String res = mvc.perform(post("/api/proposals/" + id + "/submit"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("status").asText();
    }

    @Test
    void happyPathApprovesContractsIdempotentlyAndGeneratesSchedule() throws Exception {
        score.stubFor(WireMock.get(urlPathEqualTo("/score/1")).willReturn(okJson("{\"score\":780}")));
        long id = createProposal(1, "8000", 24);
        assertThat(submit(id)).isEqualTo("APROVADA");

        String first = mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", "key-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installments.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", "key-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // mesma proposta com outra chave tambem nao gera novo contrato
        String third = mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", "key-2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode c1 = json.readTree(first);
        assertThat(json.readTree(second).get("id")).isEqualTo(c1.get("id"));
        assertThat(json.readTree(third).get("id")).isEqualTo(c1.get("id"));
        assertThat(contracts.findByProposalId(id)).isPresent();
        assertThat(contracts.count()).isEqualTo(contracts.findAll().stream().map(c -> c.getProposalId()).distinct().count());

        mvc.perform(get("/api/proposals/" + id)).andExpect(jsonPath("$.status").value("ATIVA"));
        mvc.perform(get("/api/proposals/" + id + "/history"))
                .andExpect(jsonPath("$[0].to").value("SIMULADA"))
                .andExpect(jsonPath("$[1].to").value("EM_ANALISE"))
                .andExpect(jsonPath("$[2].to").value("APROVADA"))
                .andExpect(jsonPath("$[3].to").value("CONTRATADA"))
                .andExpect(jsonPath("$[4].to").value("ATIVA"));
        mvc.perform(get("/api/proposals/" + id + "/policy-results")).andExpect(jsonPath("$.length()").value(4));

        long contractId = c1.get("id").asLong();
        mvc.perform(post("/api/contracts/" + contractId + "/installments/12/prepay"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANTECIPADA"));
        mvc.perform(post("/api/contracts/" + contractId + "/installments/12/prepay"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/contracts/" + contractId + "/installments")).andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void sameIdempotencyKeyOnAnotherProposalConflicts() throws Exception {
        score.stubFor(WireMock.get(urlPathMatching("/score/.*")).willReturn(okJson("{\"score\":780}")));
        long a = createProposal(10, "8000", 24);
        long b = createProposal(11, "8000", 24);
        submit(a);
        submit(b);
        mvc.perform(post("/api/proposals/" + a + "/contract").header("Idempotency-Key", "shared"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/proposals/" + b + "/contract").header("Idempotency-Key", "shared"))
                .andExpect(status().isConflict());
    }

    @Test
    void lowScoreRejectsAndRejectedProposalCannotBeContracted() throws Exception {
        score.stubFor(WireMock.get(urlPathEqualTo("/score/2")).willReturn(okJson("{\"score\":300}")));
        long id = createProposal(2, "8000", 24);
        assertThat(submit(id)).isEqualTo("RECUSADA");
        mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", "k-rej"))
                .andExpect(status().isConflict());
        assertThat(contracts.findByProposalId(id)).isEmpty();
        // reenviar tambem e transicao invalida
        mvc.perform(post("/api/proposals/" + id + "/submit")).andExpect(status().isConflict());
    }

    @Test
    void scoreServerErrorFallsBackToManualReviewThenManualApprovalAllowsContract() throws Exception {
        score.stubFor(WireMock.get(urlPathEqualTo("/score/3")).willReturn(serverError()));
        long id = createProposal(3, "8000", 24);
        assertThat(submit(id)).isEqualTo("ANALISE_MANUAL");
        score.verify(2, getRequestedFor(urlPathEqualTo("/score/3"))); // retry limitado (max-attempts = 2)

        mvc.perform(post("/api/proposals/" + id + "/manual-decision").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"reason\":\"documentos ok\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", "k-man"))
                .andExpect(status().isCreated());
    }

    @Test
    void scoreTimeoutFallsBackToManualReview() throws Exception {
        score.stubFor(WireMock.get(urlPathEqualTo("/score/4")).willReturn(okJson("{\"score\":800}").withFixedDelay(4500)));
        long id = createProposal(4, "8000", 24);
        assertThat(submit(id)).isEqualTo("ANALISE_MANUAL");
    }

    @Test
    void policyRejectionsDoNotNeedTheScoreService() throws Exception {
        long id = createProposal(5, "500", 24);
        assertThat(submit(id)).isEqualTo("RECUSADA");
        score.verify(0, getRequestedFor(urlPathMatching("/score/.*")));
    }

    @Test
    void validationAndNotFound() throws Exception {
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"SAC\",\"amount\":12000,\"monthlyRate\":0.01,\"term\":12}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalInterest").value(780.00));
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"SAC\",\"amount\":-1,\"monthlyRate\":0.01,\"term\":12}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/proposals/999999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/proposals/999999/contract").header("Idempotency-Key", "x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void oversizedAndMalformedInputIsRejectedWithoutLeakingInternals() throws Exception {
        String hugeTerm = "{\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":100000}";
        String body = mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content(hugeTerm))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("term").doesNotContain("com.creditflow").doesNotContain("public ");
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"PRICE\",\"amount\":99999999999999,\"monthlyRate\":0.01,\"term\":12}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":5,\"term\":12}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idempotencyKeyLengthIsValidated() throws Exception {
        score.stubFor(WireMock.get(urlPathMatching("/score/.*")).willReturn(okJson("{\"score\":780}")));
        long id = createProposal(20, "8000", 24);
        submit(id);
        mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", "k".repeat(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/proposals/" + id + "/contract").header("Idempotency-Key", " "))
                .andExpect(status().isBadRequest());
        assertThat(contracts.findByProposalId(id)).isEmpty();
    }

    @Test
    void responsesCarryDefensiveHeaders() throws Exception {
        mvc.perform(get("/api/proposals/999999"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
