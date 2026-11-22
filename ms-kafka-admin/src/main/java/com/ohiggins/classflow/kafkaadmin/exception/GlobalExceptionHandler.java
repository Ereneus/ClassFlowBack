package com.ohiggins.classflow.kafkaadmin.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce los errores del broker y de validacion a respuestas HTTP con significado.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path) {
    }

    @ExceptionHandler(KafkaAdminOperationException.class)
    public ResponseEntity<ErrorResponse> handleOperation(KafkaAdminOperationException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler({UnknownTopicOrPartitionException.class, GroupIdNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(ApiException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({TopicExistsException.class, GroupNotEmptyException.class, UnknownMemberIdException.class})
    public ResponseEntity<ErrorResponse> handleConflict(ApiException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({InvalidTopicException.class, InvalidPartitionsException.class,
            InvalidReplicationFactorException.class, InvalidConfigurationException.class,
            InvalidRequestException.class, PolicyViolationException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(ApiException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(TimeoutException ex, HttpServletRequest request) {
        log.error("Kafka admin: timeout contra el cluster en {}", request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleKafka(ApiException ex, HttpServletRequest request) {
        log.error("Kafka admin: error del broker en {}", request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getClass().getSimpleName() + ": " + ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex,
                                                                HttpServletRequest request) {
        String message = ex.getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Acceso denegado", request);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
