package com.creditflow.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** O modo demonstracao so existe quando habilitado de forma explicita. */
class DemoModeIntegrationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class Disabled {
        @Autowired MockMvc mvc;

        @Test
        void demoEndpointDoesNotExistByDefault() throws Exception {
            mvc.perform(get("/api/demo")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest(properties = "creditflow.demo.enabled=true")
    @AutoConfigureMockMvc
    class Enabled {
        @Autowired MockMvc mvc;

        @Test
        void demoEndpointListsOneClickAccountsWithoutAuthentication() throws Exception {
            mvc.perform(get("/api/demo")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.accounts[0].username").value("customer-1"))
                    .andExpect(jsonPath("$.accounts[?(@.username=='analyst')]").exists());
        }

        @Test
        void otherApiEndpointsStillRequireAuthentication() throws Exception {
            mvc.perform(get("/api/proposals")).andExpect(status().isUnauthorized());
        }
    }
}
