package com.budget.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves the API is actually locked: no session means 401 everywhere except the auth endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiEndpoints_withoutASession_return401() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insights/spending-by-category")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/subscriptions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/connections")).andExpect(status().isUnauthorized());
    }

    @Test
    void mutatingEndpoint_withSessionButNoCsrfToken_isRejected() throws Exception {
        mockMvc.perform(post("/api/transactions/1/category").with(user("owner")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authStatus_withoutASession_isReachable() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void apiEndpoint_withASession_isReachable() throws Exception {
        mockMvc.perform(get("/api/transactions").with(user("owner")))
                .andExpect(status().isOk());
    }

    @Test
    void loginEndpoint_wrongPassword_returns401NotLockout() throws Exception {
        // No credential exists in the test DB, so any password is wrong; the point is the endpoint
        // is reachable without a session and fails closed.
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }
}
