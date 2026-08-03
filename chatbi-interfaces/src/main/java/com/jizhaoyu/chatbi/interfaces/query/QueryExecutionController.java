package com.jizhaoyu.chatbi.interfaces.query;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionResponse;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionService;
import com.jizhaoyu.chatbi.interfaces.security.AuthenticatedUser;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@Validated
@RestController
@RequestMapping("/api/v1")
public class QueryExecutionController {
    private final QueryExecutionService service;

    public QueryExecutionController(QueryExecutionService service) {
        this.service = service;
    }

    @PostMapping("/approved-queries/{approvalId}:execute")
    public QueryExecutionResponse execute(
            Authentication authentication,
            @PathVariable @Size(min = 40, max = 64) String approvalId) {
        return service.execute(AuthenticatedUser.require(authentication), approvalId);
    }
}
