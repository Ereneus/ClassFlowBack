package com.ohiggins.classflow.message.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Convierte excepciones del servicio de mensajeria en respuestas uniformes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Convierte errores de validacion en un mapa campo-error.
     *
     * @param ex excepcion capturada.
     * @return respuesta HTTP 400 con los errores por campo.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Convierte un acceso a un mensaje ajeno en un error estandar. Sin este handler,
     * el de RuntimeException lo responderia como 400.
     *
     * @param ex excepcion capturada.
     * @param request peticion HTTP en curso.
     * @return respuesta HTTP 403 con el detalle del error.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Convierte la busqueda de un recurso inexistente en un error estandar.
     *
     * @param ex excepcion capturada.
     * @param request peticion HTTP en curso.
     * @return respuesta HTTP 404 con el detalle del error.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Respeta el estado HTTP de las excepciones que ya lo indican (validaciones de controllers).
     *
     * @param ex excepcion capturada.
     * @param request peticion HTTP en curso.
     * @return respuesta con el estado y motivo de la excepcion.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        return buildError(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), request);
    }

    /**
     * Convierte un fallo de comunicacion con RabbitMQ en un error estandar.
     *
     * @param ex excepcion capturada.
     * @param request peticion HTTP en curso.
     * @return respuesta HTTP 503 con el detalle del error.
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> handleAmqp(AmqpException ex, HttpServletRequest request) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no disponible: " + ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Convierte excepciones de negocio en un error estandar.
     *
     * @param ex excepcion capturada.
     * @param request peticion HTTP en curso.
     * @return respuesta HTTP 400 con el detalle del error.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Convierte excepciones inesperadas en un error estandar.
     *
     * @param ex excepcion capturada.
     * @param request peticion HTTP en curso.
     * @return respuesta HTTP 500 con el detalle del error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred: " + ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
