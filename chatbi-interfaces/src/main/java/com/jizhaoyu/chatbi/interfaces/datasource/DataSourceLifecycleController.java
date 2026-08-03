package com.jizhaoyu.chatbi.interfaces.datasource;

import com.jizhaoyu.chatbi.application.datasource.ConnectionProbeResult;
import com.jizhaoyu.chatbi.application.datasource.DataSourceLifecycleService;
import com.jizhaoyu.chatbi.interfaces.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-sources")
public class DataSourceLifecycleController {
    private final DataSourceLifecycleService service;

    public DataSourceLifecycleController(DataSourceLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/{id}:test")
    public TestConnectionResponse test(Authentication authentication, @PathVariable UUID id) {
        return TestConnectionResponse.from(service.test(AuthenticatedUser.require(authentication), id));
    }

    public record TestConnectionResponse(String status, String code, String message) {
        static TestConnectionResponse from(ConnectionProbeResult result) {
            return new TestConnectionResponse(result.status().name(), result.code(), result.message());
        }
    }
}
