package com.jizhaoyu.chatbi.interfaces.web;

import com.jizhaoyu.chatbi.application.execution.QueryExecutionFailure;
import com.jizhaoyu.chatbi.application.execution.QueryExecutionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(QueryExecutionFailure.class)
    ResponseEntity<ApiError> queryExecution(QueryExecutionFailure exception) {
        HttpStatus status = exception.status() == QueryExecutionStatus.TIMEOUT
                ? HttpStatus.REQUEST_TIMEOUT : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ApiError(exception.getMessage(), "Query execution failed", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Request validation failed", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiError("REQUEST_BODY_INVALID", "Request body is invalid", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        String code = exception.getReason() == null ? "REQUEST_REJECTED" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(code, "Request rejected", null));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ApiError> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", "Access denied", null));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ApiError> business(RuntimeException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage(), "Request rejected", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "Unexpected error", null));
    }
}
