package com.creditflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditflow.policy.PolicyContext;
import com.creditflow.policy.PolicyEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Regressao: excecao inesperada na politica nao deixa a proposta presa em EM_ANALISE. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ANALYST")
class PolicyFailureIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PolicyEngine policyEngine;

    @Test
    void unexpectedPolicyErrorGoesToManualReview() throws Exception {
        when(policyEngine.evaluate(any(PolicyContext.class))).thenThrow(new IllegalStateException("boom"));
        String body = "{\"customerId\":1,\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":6,"
                + "\"monthlyIncome\":5000,\"existingCommitments\":0,\"relationshipMonths\":12}";
        String created = mvc.perform(post("/api/proposals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(post("/api/proposals/" + id + "/submit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANALISE_MANUAL"));
        mvc.perform(get("/api/proposals/" + id + "/policy-results"))
                .andExpect(jsonPath("$[0].rule").value("POLICY_ERROR"));
        assertThat(id).isPositive();
    }
}
