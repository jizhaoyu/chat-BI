package com.jizhaoyu.chatbi.interfaces.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.application.datasource.DataSourceApplicationService;
import com.jizhaoyu.chatbi.application.datasource.DataSourceCommand;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import com.jizhaoyu.chatbi.interfaces.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import com.jizhaoyu.chatbi.interfaces.web.GlobalExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = DataSourceController.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@ContextConfiguration(classes = {DataSourceController.class, SecurityConfiguration.class,
        GlobalExceptionHandler.class, DataSourceControllerSecurityTest.TestConfiguration.class})
class DataSourceControllerSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/v1/data-sources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsCreateWithoutCsrf() throws Exception {
        mvc.perform(post("/api/v1/data-sources").with(as(Role.DATA_ADMIN))
                        .contentType("application/json").content(requestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCreateForAnalyst() throws Exception {
        mvc.perform(post("/api/v1/data-sources").with(as(Role.ANALYST)).with(csrf())
                        .contentType("application/json").content(requestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void dataAdminCreatesDraftWithoutSecretFieldsInResponse() throws Exception {
        mvc.perform(post("/api/v1/data-sources").with(as(Role.DATA_ADMIN)).with(csrf())
                        .contentType("application/json").content(requestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.credentialRef").doesNotExist())
                .andExpect(jsonPath("$.host").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    private String requestJson() throws Exception {
        return mapper.writeValueAsString(new DataSourceController.CreateDataSourceRequest(
                "sales", "sample-sales", 3306, "sample_sales", "reader", "env/sample-reader",
                DataSourceDialect.MYSQL, 1000, 30));
    }

    private static RequestPostProcessor as(Role role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(role));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, SecurityConfiguration.authorities(principal)));
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        DataSourceApplicationService dataSourceApplicationService() {
            return new DataSourceApplicationService(new MemoryRepository(), event -> { });
        }
    }

    static class MemoryRepository implements DataSourceRepository {
        private final List<DataSourceView> values = new ArrayList<>();
        public DataSourceView save(UUID tenantId, DataSourceCommand command) {
            DataSourceView value = new DataSourceView(UUID.randomUUID(), command.name(), command.host(), command.port(),
                    command.database(), command.username(), command.credentialRef(), command.dialect(), DataSourceStatus.DRAFT,
                    command.maxRows(), command.timeoutSeconds());
            values.add(value);
            return value;
        }
        public List<DataSourceView> findAllByTenant(UUID tenantId) { return List.copyOf(values); }
        public Optional<DataSourceView> findByTenantAndId(UUID tenantId, UUID id) { return values.stream().filter(value -> value.id().equals(id)).findFirst(); }
        public DataSourceView update(UUID tenantId, UUID id, DataSourceCommand command) { throw new UnsupportedOperationException(); }
        public DataSourceView updateStatus(UUID tenantId, UUID id, DataSourceStatus status) { throw new UnsupportedOperationException(); }
        public void disable(UUID tenantId, UUID id) { throw new UnsupportedOperationException(); }
    }
}
