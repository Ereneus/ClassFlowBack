package com.ohiggins.classflow.analytics.kafka;

/**
 * El payload de un evento Kafka no es valido (faltan campos o tienen valores imposibles).
 * Reintentarlo no lo va a arreglar, por eso el error handler la marca como no reintentable
 * y el mensaje se envia directo al DLT.
 */
public class InvalidKafkaEventException extends RuntimeException {

    public InvalidKafkaEventException(String message) {
        super(message);
    }
}
