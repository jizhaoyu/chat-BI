package com.jizhaoyu.chatbi.interfaces.datasource;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.jizhaoyu.chatbi.application.datasource.DataSourceApplicationService;
import com.jizhaoyu.chatbi.application.datasource.DataSourceCommand;
import com.jizhaoyu.chatbi.application.datasource.DataSourceView;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;
import com.jizhaoyu.chatbi.domain.datasource.DataSourceStatus;
import com.jizhaoyu.chatbi.interfaces.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-sources")
public class DataSourceController {
    private final DataSourceApplicationService service;

    public DataSourceController(DataSourceApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<DataSourceResponse> list(Authentication authentication) {
        return service.list(AuthenticatedUser.require(authentication)).stream().map(DataSourceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceResponse create(Authentication authentication, @Valid @RequestBody CreateDataSourceRequest request) {
        DataSourceCommand command = new DataSourceCommand(request.name(), request.host(), request.port(), request.database(),
                request.username(), request.password(), request.dialect(), request.maxRows(), request.timeoutSeconds());
        return DataSourceResponse.from(service.create(AuthenticatedUser.require(authentication), command));
    }

    @GetMapping("/{id}")
    public DataSourceResponse get(Authentication authentication, @PathVariable UUID id) {
        return DataSourceResponse.from(service.get(AuthenticatedUser.require(authentication), id));
    }

    @PutMapping("/{id}")
    public DataSourceResponse update(Authentication authentication, @PathVariable UUID id,
                                     @Valid @RequestBody CreateDataSourceRequest request) {
        DataSourceCommand command = new DataSourceCommand(request.name(), request.host(), request.port(), request.database(),
                request.username(), request.password(), request.dialect(), request.maxRows(), request.timeoutSeconds());
        return DataSourceResponse.from(service.update(AuthenticatedUser.require(authentication), id, command));
    }

    @PostMapping("/{id}:disable")
    public DataSourceResponse disable(Authentication authentication, @PathVariable UUID id) {
        return DataSourceResponse.from(service.disable(AuthenticatedUser.require(authentication), id));
    }

    public record CreateDataSourceRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 253) String host,
            @Min(1) @Max(65535) int port,
            @NotBlank @Size(max = 64) String database,
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(min = 12, max = 1024) String password,
            @NotNull DataSourceDialect dialect,
            @Min(1) @Max(1_000_000) int maxRows,
            @Min(1) @Max(600) int timeoutSeconds) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object ignored) {
            throw new IllegalArgumentException("UNKNOWN_REQUEST_FIELD");
        }
    }

    public record DataSourceResponse(UUID id, String name, DataSourceDialect dialect, DataSourceStatus status, int maxRows, int timeoutSeconds) {
        static DataSourceResponse from(DataSourceView view) {
            return new DataSourceResponse(view.id(), view.name(), view.dialect(), view.status(), view.maxRows(), view.timeoutSeconds());
        }
    }
}
