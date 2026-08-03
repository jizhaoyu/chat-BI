package com.jizhaoyu.chatbi.interfaces.query;

import com.jizhaoyu.chatbi.application.sqlguard.SqlObjectReference;
import com.jizhaoyu.chatbi.application.sqlguard.SqlValidationApproval;
import com.jizhaoyu.chatbi.application.sqlguard.SqlValidationService;
import com.jizhaoyu.chatbi.interfaces.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SqlValidationController {
    private final SqlValidationService service;

    public SqlValidationController(SqlValidationService service) {
        this.service = service;
    }

    @PostMapping("/query-candidates:validate")
    public ValidationResponse validate(
            Authentication authentication, @Valid @RequestBody ValidationRequest request) {
        SqlValidationApproval approval = service.validate(
                AuthenticatedUser.require(authentication), request.dataSourceId(), request.sql());
        return new ValidationResponse(approval.approvalId(), approval.normalizedSql(), approval.effectiveLimit(),
                approval.references(), approval.expiresAt());
    }

    public record ValidationRequest(
            @NotNull UUID dataSourceId,
            @NotBlank @Size(max = 20_000) String sql) {
    }

    public record ValidationResponse(
            String approvalId,
            String normalizedSql,
            int effectiveLimit,
            List<SqlObjectReference> references,
            Instant expiresAt) {
    }
}
