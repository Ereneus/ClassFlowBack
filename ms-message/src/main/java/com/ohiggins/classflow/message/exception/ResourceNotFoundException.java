package com.ohiggins.classflow.message.exception;

/**
 * Indica que el recurso solicitado no existe; GlobalExceptionHandler la responde como 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
