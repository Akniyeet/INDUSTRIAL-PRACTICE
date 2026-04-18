package com.webizon.common.web;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centralised error → {@link ProblemDetail} (RFC 9457) mapping.
 *
 * <p>Every response shares the same shape:
 * <pre>
 * {
 *   "type":     "https://webizon.kz/problems/validation",
 *   "title":    "Validation failed",
 *   "status":   400,
 *   "detail":   "…human-readable…",
 *   "instance": "/api/v1/tenants",
 *   "traceId":  "8f3a…",
 *   "timestamp":"2026-04-10T12:00:00Z",
 *   "errors":   { "slug": "…" }   // validation only
 * }
 * </pre>
 *
 * <p>We intentionally do NOT leak exception class names, stack traces, or
 * bean paths in production responses — those go to the logs only.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://webizon.kz/problems/";

    // ------------------------------------------------------------------
    // Client errors (4xx)
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        ProblemDetail pd = baseProblem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more fields failed validation.");
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onConstraint(ConstraintViolationException ex) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        cv -> cv.getMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        ProblemDetail pd = baseProblem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "Request parameters failed validation.");
        pd.setProperty("errors", violations);
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return baseProblem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request",
                "Path or query parameter '" + ex.getName() + "' has an invalid value.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return baseProblem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onIllegalState(IllegalStateException ex) {
        return baseProblem(HttpStatus.CONFLICT, "conflict", "State conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return baseProblem(HttpStatus.CONFLICT, "conflict", "Database constraint violation",
                "The request conflicts with the current state of the resource.");
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ProblemDetail onOptimisticLock(Exception ex) {
        return baseProblem(HttpStatus.CONFLICT, "optimistic-lock",
                "Concurrent modification",
                "This resource was modified by someone else; please refresh and retry.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return baseProblem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to perform this operation.");
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail onSecurity(SecurityException ex) {
        // TenantEntityListener throws these on cross-tenant writes. Never
        // leak the detail to the client — log and return a generic 403.
        log.error("Security exception (possible cross-tenant write attempt): {}", ex.getMessage());
        return baseProblem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "Operation denied.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail onResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String slug = status.is4xxClientError() ? "client-error" : "internal";
        return baseProblem(status, slug, status.getReasonPhrase(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    // ------------------------------------------------------------------
    // Server errors (5xx)
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [correlationId={}]", correlationId, ex);
        ProblemDetail pd = baseProblem(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                "Internal server error",
                "An unexpected error occurred. Reference id: " + correlationId);
        pd.setProperty("correlationId", correlationId);
        return pd;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ProblemDetail baseProblem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail);
        pd.setType(URI.create(PROBLEM_BASE + slug));
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
