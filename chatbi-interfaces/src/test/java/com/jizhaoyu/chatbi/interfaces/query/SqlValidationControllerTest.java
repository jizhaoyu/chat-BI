package com.jizhaoyu.chatbi.interfaces.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhaoyu.chatbi.application.sqlguard.SqlValidationService;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import com.jizhaoyu.chatbi.interfaces.security.SecurityConfiguration;
import com.jizhaoyu.chatbi.interfaces.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SqlValidationController.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@ContextConfiguration(classes = {SqlValidationController.class, SecurityConfiguration.class,
        GlobalExceptionHandler.class, SqlValidationControllerTest.Config.class})
class SqlValidationControllerTest {
    private static final UUID SOURCE = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SqlValidationService service;

    @BeforeEach
    void clearServiceInvocations() {
        clearInvocations(service);
    }

    @Test
    void unauthenticatedRequestIsRejectedBeforeValidation() throws Exception {
        mvc.perform(post("/api/v1/query-candidates:validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new SqlValidationController.ValidationRequest(
                                SOURCE, "SELECT id FROM fact_order"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void authenticatedAnalystReceivesOnlyPublicApprovalFields() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Role.ANALYST));
        when(service.validate(eq(principal), eq(SOURCE), any())).thenReturn(
                new com.jizhaoyu.chatbi.application.sqlguard.SqlValidationApproval(
                        "public-approval-token", "SELECT id FROM fact_order LIMIT 10", 10, List.of(),
                        Instant.parse("2026-08-03T00:02:00Z")));

        mvc.perform(post("/api/v1/query-candidates:validate").with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, SecurityConfiguration.authorities(principal))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new SqlValidationController.ValidationRequest(
                                SOURCE, "SELECT id FROM fact_order"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value("public-approval-token"))
                .andExpect(jsonPath("$.effectiveLimit").value(10))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tokenHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("policyHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("authorizationVersion"))));
    }

    @Configuration
    static class Config {
        @Bean SqlValidationService sqlValidationService() { return mock(SqlValidationService.class); }
    }
}
