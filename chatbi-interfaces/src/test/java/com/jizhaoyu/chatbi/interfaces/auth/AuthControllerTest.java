package com.jizhaoyu.chatbi.interfaces.auth;

import com.jizhaoyu.chatbi.application.identity.IdentityPort;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import com.jizhaoyu.chatbi.interfaces.security.SecurityConfiguration;
import com.jizhaoyu.chatbi.interfaces.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuthController.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@ContextConfiguration(classes = {AuthController.class, SecurityConfiguration.class,
        GlobalExceptionHandler.class, AuthControllerTest.TestConfiguration.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void returnsGenericUnauthorizedResponseForInvalidCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"unknown\",\"password\":\"do-not-leak\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Request rejected"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("do-not-leak"));
    }

    @Test
    void createsServerSideSecurityContextForValidCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"data-admin\",\"password\":\"valid-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("DATA_ADMIN"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)
                        .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("valid-password"));
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        IdentityPort identityPort() {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of(Role.DATA_ADMIN));
            return (username, password) -> "data-admin".equals(username) && "valid-password".equals(password)
                    ? Optional.of(principal) : Optional.empty();
        }
    }
}
