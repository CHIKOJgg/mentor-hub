package com.mentorhub.security;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.repository.AdminTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminTokenRepository adminTokenRepository;

    @Test
    @DisplayName("System diagnostics require an authenticated administrator")
    void testSystemStatusRequiresToken() throws Exception {
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unconfigured CORS does not allow arbitrary browser origins")
    void testCorsRejectsUntrustedOriginByDefault() throws Exception {
        mockMvc.perform(options("/api/students")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Admin-Token"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Protected endpoint returns 401 Unauthorized when token is missing")
    void testProtectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/inbox"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint with valid X-Admin-Token header passes authentication")
    void testProtectedEndpointWithValidHeaderToken() throws Exception {
        mockMvc.perform(get("/api/inbox")
                        .header("X-Admin-Token", "magic-ksenia-mentor-token-998877"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Successfully authenticated (passes security filter, reaches MVC handler)
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });
    }

    @Test
    @DisplayName("Protected endpoint rejects valid credentials supplied in a query parameter")
    void testProtectedEndpointRejectsQueryParamToken() throws Exception {
        mockMvc.perform(get("/api/inbox")
                        .param("token", "magic-katya-curator-token-112233"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint with invalid token returns 401 Unauthorized")
    void testProtectedEndpointWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/inbox")
                        .header("X-Admin-Token", "invalid-fake-token-12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint with expired token returns 401 Unauthorized")
    void testProtectedEndpointWithExpiredTokenReturns401() throws Exception {
        String expiredTokenStr = "expired-token-" + UUID.randomUUID();
        AdminToken expiredToken = AdminToken.builder()
                .adminName("Тест")
                .role("CURATOR")
                .token(AdminTokenHasher.hash(expiredTokenStr))
                .isActive(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        adminTokenRepository.save(expiredToken);

        mockMvc.perform(get("/api/inbox")
                        .header("X-Admin-Token", expiredTokenStr))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Curator role has the same admin access to deleteStudent as mentor")
    void testCuratorCanDeleteStudent() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/students/1")
                        .header("X-Admin-Token", "magic-katya-curator-token-112233"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Mentor role is authorized to access deleteStudent endpoint")
    void testMentorCanDeleteStudent() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/students/1")
                        .header("X-Admin-Token", "magic-ksenia-mentor-token-998877"))
                .andExpect(status().isNoContent());
    }
}
