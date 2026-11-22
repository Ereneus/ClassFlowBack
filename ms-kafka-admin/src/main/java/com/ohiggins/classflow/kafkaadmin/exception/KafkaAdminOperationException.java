package com.ohiggins.classflow.kafkaadmin.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Operacion de administracion rechazada por una regla del servicio (no por el broker), por
 * ejemplo eliminar un topico protegido o resetear offsets de un grupo con consumidores activos.
 */
@Getter
public class KafkaAdminOperationException extends RuntimeException {

    private final HttpStatus status;

    public KafkaAdminOperationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
