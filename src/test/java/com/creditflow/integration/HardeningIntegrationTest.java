package com.creditflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditflow.api.GlobalExceptionHandler;
import com.creditflow.common.InvalidInputException;
import com.creditflow.security.AuthFailureThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;

/** Limite de corpo, limitacao de tentativas de autenticacao, cabecalhos e mensagens de erro seguras. */
@SpringBootTest
@AutoConfigureMockMvc
class HardeningIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AuthFailureThrottle throttle;

    @BeforeEach
    void reset() {
        throttle.reset();
    }

    @Test
    void oversizedBodyIsRefusedWith413() throws Exception {
        String padding = "x".repeat(70 * 1024);
        mvc.perform(post("/api/simulations").with(httpBasic("customer-1", "test-customer-1-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"PRICE\",\"amount\":1000,\"monthlyRate\":0.01,\"term\":6,\"pad\":\"" + padding + "\"}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void repeatedAuthenticationFailuresAreThrottledBeforeTheHashIsComputed() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/me").with(httpBasic("customer-1", "errada-" + i))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/me").with(httpBasic("customer-1", "errada-final")))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "300"));
        mvc.perform(get("/api/me").with(httpBasic("customer-1", "test-customer-1-pw"))).andExpect(status().isTooManyRequests());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        throttle.reset();
        mvc.perform(get("/api/me").with(httpBasic("customer-1", "test-customer-1-pw"))).andExpect(status().isOk());
    }

    @Test
    void requestsWithoutCredentialsDoNotCountAsFailures() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/me").with(httpBasic("customer-1", "test-customer-1-pw"))).andExpect(status().isOk());
    }

    @Test
    void permissionsPolicyHeaderIsSent() throws Exception {
        mvc.perform(get("/index.html")).andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")));
    }

    @Test
    void onlyDomainValidationMessagesReachTheClient() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ProblemDetail generic = invoke(handler, new IllegalArgumentException("org.hibernate.internal.Detail at jdbc:postgresql://db"));
        assertThat(generic.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(generic.getDetail()).isEqualTo("Requisicao invalida").doesNotContain("hibernate").doesNotContain("jdbc");
        ProblemDetail ours = invoke(handler, new InvalidInputException("term must be at most 480"));
        assertThat(ours.getDetail()).isEqualTo("term must be at most 480");
    }

    private static ProblemDetail invoke(GlobalExceptionHandler handler, IllegalArgumentException e) {
        try {
            var method = e instanceof InvalidInputException
                    ? GlobalExceptionHandler.class.getDeclaredMethod("invalidInput", InvalidInputException.class)
                    : GlobalExceptionHandler.class.getDeclaredMethod("badRequest", IllegalArgumentException.class);
            method.setAccessible(true);
            return (ProblemDetail) method.invoke(handler, e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
