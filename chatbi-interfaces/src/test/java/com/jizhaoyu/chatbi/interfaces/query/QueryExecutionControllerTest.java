package com.jizhaoyu.chatbi.interfaces.query;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionResponse;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionResult;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionFailure;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionService;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import com.jizhaoyu.chatbi.interfaces.security.SecurityConfiguration;
import com.jizhaoyu.chatbi.interfaces.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = QueryExecutionController.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@ContextConfiguration(classes = {QueryExecutionController.class, SecurityConfiguration.class,
        GlobalExceptionHandler.class, QueryExecutionControllerTest.Config.class})
class QueryExecutionControllerTest {
    private static final String APPROVAL = "a".repeat(43);
    @Autowired MockMvc mvc;
    @Autowired QueryExecutionService service;

    @BeforeEach
    void clearService() {
        clearInvocations(service);
    }

    @Test
    void rejectsUnauthenticatedExecutionBeforeService() throws Exception {
        mvc.perform(post("/api/v1/approved-queries/{approvalId}:execute", APPROVAL).with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void executesByApprovalIdWithoutAcceptingCallerSql() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Role.ANALYST));
        QueryExecutionResponse response = new QueryExecutionResponse(
                UUID.randomUUID(), QueryExecutionStatus.SUCCEEDED,
                new QueryExecutionResult(List.of(), List.of(), false, "digest"));
        when(service.execute(principal, APPROVAL)).thenReturn(response);

        mvc.perform(post("/api/v1/approved-queries/{approvalId}:execute", APPROVAL)
                        .with(csrf()).with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, SecurityConfiguration.authorities(principal))))
                        .contentType("application/json")
                        .content("{\"sql\":\"DROP TABLE fact_order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(response.executionId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("DROP TABLE"))));
    }

    @Test
    void mapsConcurrencyExhaustionToTooManyRequests() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Role.ANALYST));
        when(service.execute(principal, APPROVAL)).thenThrow(
                new QueryExecutionFailure(QueryExecutionStatus.FAILED, "QUERY_CONCURRENCY_EXCEEDED"));

        mvc.perform(post("/api/v1/approved-queries/{approvalId}:execute", APPROVAL)
                        .with(csrf()).with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, SecurityConfiguration.authorities(principal)))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUERY_CONCURRENCY_EXCEEDED"));
    }

    @Configuration
    static class Config {
        @Bean QueryExecutionService queryExecutionService() { return mock(QueryExecutionService.class); }
    }
}
