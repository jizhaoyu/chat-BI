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

    @Test
    void rejectsClientSuppliedCredentialReference() throws Exception {
        String json = requestJson().replace("\"password\":\"reader-secret-password\"",
                "\"password\":\"reader-secret-password\",\"credentialRef\":\"env/PLATFORM_DB_PASSWORD\"");

        mvc.perform(post("/api/v1/data-sources").with(as(Role.DATA_ADMIN)).with(csrf())
                        .contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
    }

    private String requestJson() throws Exception {
        return mapper.writeValueAsString(java.util.Map.of(
                "name", "sales",
                "host", "sample-sales.example.com",
                "port", 3306,
                "database", "sample_sales",
                "username", "reader",
                "password", "reader-secret-password",
                "dialect", DataSourceDialect.MYSQL,
                "maxRows", 1000,
                "timeoutSeconds", 30));
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
            return new DataSourceApplicationService(new MemoryRepository(), event -> { },
                    (tenant, dataSource, secret) -> "credential/" + dataSource,
                    new NoopExternalPool());
        }
    }

    static class MemoryRepository implements DataSourceRepository {
        private final List<DataSourceView> values = new ArrayList<>();
        public DataSourceView save(UUID tenantId, UUID id, DataSourceCommand command, String credentialRef) {
            DataSourceView value = new DataSourceView(id, command.name(), command.host(), command.port(),
                    command.database(), command.username(), credentialRef, command.dialect(), DataSourceStatus.DRAFT,
                    command.maxRows(), command.timeoutSeconds());
            values.add(value);
            return value;
        }
        public List<DataSourceView> findAllByTenant(UUID tenantId) { return List.copyOf(values); }
        public Optional<DataSourceView> findByTenantAndId(UUID tenantId, UUID id) { return values.stream().filter(value -> value.id().equals(id)).findFirst(); }
        public DataSourceView update(UUID tenantId, UUID id, DataSourceCommand command, String credentialRef) { throw new UnsupportedOperationException(); }
        public DataSourceView transitionStatus(
                UUID tenantId, UUID id, DataSourceStatus expected, DataSourceStatus target) {
            throw new UnsupportedOperationException();
        }
        public void disable(UUID tenantId, UUID id) { throw new UnsupportedOperationException(); }
    }

    static final class NoopExternalPool implements com.jizhaoyu.chatbi.application.datasource.ExternalDataSourcePool {
        @Override
        public javax.sql.DataSource getOrCreate(
                com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionSpec connectionSpec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy(UUID tenantId, UUID dataSourceId) {
        }

        @Override
        public void close() {
        }
    }
}
