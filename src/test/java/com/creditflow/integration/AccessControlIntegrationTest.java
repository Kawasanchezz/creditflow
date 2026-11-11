package com.creditflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Autenticacao (HTTP Basic com BCrypt) e verificacao de propriedade (IDOR) com as contas reais da configuracao. */
@SpringBootTest
@AutoConfigureMockMvc
class AccessControlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String CUSTOMER_1 = "customer-1";
    private static final String CUSTOMER_1_PW = "test-customer-1-pw";
    private static final String CUSTOMER_2 = "customer-2";
    private static final String CUSTOMER_2_PW = "test-customer-2-pw";

    private static String proposalBody(long customerId) {
        return "{\"customerId\":" + customerId + ",\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":6,"
                + "\"monthlyIncome\":5000,\"existingCommitments\":0,\"relationshipMonths\":12}";
    }

    private long createAsCustomer1() throws Exception {
        String res = mvc.perform(post("/api/proposals").with(httpBasic(CUSTOMER_1, CUSTOMER_1_PW))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody(1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void requestsWithoutOrWithWrongCredentialsAreRejected() throws Exception {
        mvc.perform(get("/api/proposals/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/proposals/1").with(httpBasic(CUSTOMER_1, "wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/proposals/1").with(httpBasic("nobody", "x"))).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateProposalForAnotherCustomer() throws Exception {
        mvc.perform(post("/api/proposals").with(httpBasic(CUSTOMER_1, CUSTOMER_1_PW))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody(2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotReadOrActOnAnotherCustomersResources() throws Exception {
        long id = createAsCustomer1();

        mvc.perform(get("/api/proposals/" + id).with(httpBasic(CUSTOMER_1, CUSTOMER_1_PW)))
                .andExpect(status().isOk());

        for (String path : new String[]{"", "/history", "/policy-results"}) {
            mvc.perform(get("/api/proposals/" + id + path).with(httpBasic(CUSTOMER_2, CUSTOMER_2_PW)))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/proposals/" + id + "/submit").with(httpBasic(CUSTOMER_2, CUSTOMER_2_PW)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/proposals/" + id + "/contract").with(httpBasic(CUSTOMER_2, CUSTOMER_2_PW))
                        .header("Idempotency-Key", "x")).andExpect(status().isNotFound());
        mvc.perform(get("/api/contracts/" + id + "/installments").with(httpBasic(CUSTOMER_2, CUSTOMER_2_PW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualDecisionIsRestrictedToAnalysts() throws Exception {
        long id = createAsCustomer1();
        String body = "{\"approved\":true,\"reason\":\"ok\"}";
        mvc.perform(post("/api/proposals/" + id + "/manual-decision").with(httpBasic(CUSTOMER_1, CUSTOMER_1_PW))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        int analystStatus = mvc.perform(post("/api/proposals/" + id + "/manual-decision")
                        .with(httpBasic("analyst", "test-analyst-pw"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
        assertThat(analystStatus).isEqualTo(409); // passou pela autorizacao; SIMULADA nao pode ser decidida
    }

    @Test
    void analystCanReadAnyProposal() throws Exception {
        long id = createAsCustomer1();
        mvc.perform(get("/api/proposals/" + id).with(httpBasic("analyst", "test-analyst-pw")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proposals/999999").with(httpBasic("analyst", "test-analyst-pw")))
                .andExpect(status().isNotFound());
    }
}
