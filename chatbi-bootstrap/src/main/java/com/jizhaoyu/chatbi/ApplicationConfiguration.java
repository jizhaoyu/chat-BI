package com.jizhaoyu.chatbi;

import com.jizhaoyu.chatbi.application.audit.AuditPort;
import com.jizhaoyu.chatbi.application.datasource.DataSourceApplicationService;
import com.jizhaoyu.chatbi.application.datasource.DataSourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ApplicationConfiguration {
    @Bean
    DataSourceApplicationService dataSourceApplicationService(DataSourceRepository repository, AuditPort auditPort) {
        return new DataSourceApplicationService(repository, auditPort);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
