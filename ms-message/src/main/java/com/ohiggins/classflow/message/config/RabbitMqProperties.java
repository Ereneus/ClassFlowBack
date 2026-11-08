package com.ohiggins.classflow.message.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * Nombres de la topologia RabbitMQ de ClassFlow, definidos de forma centralizada en
 * application.properties bajo el prefijo {@code classflow.rabbitmq}.
 *
 * @param eventsExchange          exchange topic donde se publican los eventos de dominio.
 * @param messageCreatedQueue     cola que recibe los eventos de mensaje creado.
 * @param messageCreatedRoutingKey routing key del evento de mensaje creado.
 * @param deadLetterExchange      exchange al que se envian los mensajes rechazados.
 * @param deadLetterQueue         cola donde quedan guardados los mensajes rechazados.
 */
@Validated
@ConfigurationProperties(prefix = "classflow.rabbitmq")
public record RabbitMqProperties(
        @NotBlank String eventsExchange,
        @NotBlank String messageCreatedQueue,
        @NotBlank String messageCreatedRoutingKey,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue
) {

    /**
     * Recursos de los que depende el flujo de mensajes; la API de administracion no los modifica.
     *
     * @return nombres de exchanges y colas reservados.
     */
    public Set<String> reservedNames() {
        return Set.of(eventsExchange, deadLetterExchange, messageCreatedQueue, deadLetterQueue);
    }
}
