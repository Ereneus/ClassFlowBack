package com.ohiggins.classflow.message.exception;

/**
 * Se lanza al intentar modificar una cola o exchange que usa el flujo de mensajes de ClassFlow.
 */
public class ReservedRabbitResourceException extends RuntimeException {

    public ReservedRabbitResourceException(String name) {
        super("El recurso " + name + " es usado por ClassFlow y no se puede modificar");
    }
}
