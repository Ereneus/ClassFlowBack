package com.ohiggins.classflow.notification.event;

/**
 * El evento recibido no tiene los datos minimos para crear la notificacion.
 * No tiene sentido reintentarlo: el listener lo envia directo a la DLQ.
 */
public class InvalidMessageEventException extends RuntimeException {

    public InvalidMessageEventException(String message) {
        super(message);
    }
}
