package com.ohiggins.classflow.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
}
