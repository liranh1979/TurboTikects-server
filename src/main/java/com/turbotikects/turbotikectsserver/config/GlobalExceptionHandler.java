package com.turbotikects.turbotikectsserver.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Without this, Spring Boot's default error body omits "message" for every error response
 * (server.error.include-message defaults to "never", and this app never overrode it) — so every
 * ResponseStatusException thrown anywhere in the app, no matter how clear its reason string, was
 * silently discarded before reaching the client. The frontend's various catch blocks already do
 * {@code err?.response?.data?.message || <generic fallback text>}, so this was always
 * quietly falling through to the generic fallback, hiding the real cause of e.g. "no active AI
 * configuration" or "could not connect to MCP server" behind an unrelated-looking message —
 * confirmed live while diagnosing exactly that symptom for the AI Workflow Builder's MCP support.
 *
 * Deliberately scoped to ONLY {@link ResponseStatusException} — those are always a developer-
 * authored, safe-to-show reason string (never a raw internal exception). Any other, truly
 * unexpected exception (NPE, SQL error, etc.) is left to Spring Boot's existing default handling,
 * which still omits "message" — this does not turn on server.error.include-message globally,
 * so uncaught internal exception details still never reach a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", status != null ? status.getReasonPhrase() : "Error",
                "message", ex.getReason() != null ? ex.getReason() : ""
        ));
    }
}
