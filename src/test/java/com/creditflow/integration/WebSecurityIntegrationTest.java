package com.creditflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Interface web estatica, CSRF, cabecalhos e os endpoints de apoio da interface (/api/me, listagem, contrato). */
@SpringBootTest
@AutoConfigureMockMvc
class WebSecurityIntegrationTest {

    @Autowired MockMvc mvc;

    private static final String C1 = "customer-1", C1_PW = "test-customer-1-pw";
    private static final String C2 = "customer-2", C2_PW = "test-customer-2-pw";

    private static String proposalBody(long customerId) {
        return "{\"customerId\":" + customerId + ",\"system\":\"SAC\",\"amount\":2000,\"monthlyRate\":0.01,\"term\":4,"
                + "\"monthlyIncome\":5000,\"existingCommitments\":0,\"relationshipMonths\":12}";
    }

    @Test
    void staticPageIsPublicAndCarriesContentSecurityPolicy() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CreditFlow")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/app.css")).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedApiRequestDoesNotTriggerBrowserBasicPrompt() throws Exception {
        mvc.perform(get("/api/proposals")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void cookieBearingStateChangingRequestWithoutExplicitCredentialIsRejectedByCsrf() throws Exception {
        mvc.perform(post("/api/simulations").cookie(new Cookie("ambient", "1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":6}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void meReturnsRoleAndCustomer() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic(C1, C1_PW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerId").value(1)).andExpect(jsonPath("$.username").value(C1));
        mvc.perform(get("/api/me").with(httpBasic("analyst", "test-analyst-pw")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ANALYST"));
    }

    @Test
    void listingIsScopedToTheCustomerAndAnalystSeesAll() throws Exception {
        mvc.perform(post("/api/proposals").with(httpBasic(C1, C1_PW)).contentType(MediaType.APPLICATION_JSON)
                .content(proposalBody(1))).andExpect(status().isCreated());
        String mine = mvc.perform(get("/api/proposals").with(httpBasic(C1, C1_PW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("\"customerId\":1").doesNotContain("\"customerId\":2");
        String other = mvc.perform(get("/api/proposals").with(httpBasic(C2, C2_PW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(other).doesNotContain("\"customerId\":1");
        String all = mvc.perform(get("/api/proposals").with(httpBasic("analyst", "test-analyst-pw")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(all).contains("\"customerId\":1");
    }

    @Test
    void contractLookupIs404WithoutContractAndForForeignCustomers() throws Exception {
        String res = mvc.perform(post("/api/proposals").with(httpBasic(C1, C1_PW)).contentType(MediaType.APPLICATION_JSON)
                .content(proposalBody(1))).andReturn().getResponse().getContentAsString();
        String id = res.replaceAll(".*\"id\":(\\d+).*", "$1");
        mvc.perform(get("/api/proposals/" + id + "/contract").with(httpBasic(C1, C1_PW))).andExpect(status().isNotFound());
        mvc.perform(get("/api/proposals/" + id + "/contract").with(httpBasic(C2, C2_PW))).andExpect(status().isNotFound());
    }
}
