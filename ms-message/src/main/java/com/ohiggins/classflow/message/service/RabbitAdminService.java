package com.ohiggins.classflow.message.service;

import com.ohiggins.classflow.message.config.RabbitMqProperties;
import com.ohiggins.classflow.message.exception.ReservedRabbitResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.stereotype.Service;

/**
 * Encapsula la administracion de colas, exchanges y bindings en RabbitMQ.
 * Los recursos de los que depende el flujo de mensajes estan reservados y no se modifican.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitAdminService {

    private final AmqpAdmin amqpAdmin;
    private final RabbitMqProperties props;

    public void createQueue(String name, boolean durable) {
        requireModifiable(name);
        Queue queue = durable ? QueueBuilder.durable(name).build() : QueueBuilder.nonDurable(name).build();
        amqpAdmin.declareQueue(queue);
        log.info("Cola RabbitMQ creada: {} (durable={})", name, durable);
    }

    public void deleteQueue(String name) {
        requireModifiable(name);
        amqpAdmin.deleteQueue(name);
        log.info("Cola RabbitMQ eliminada: {}", name);
    }

    public void createExchange(String name, ExchangeType type, boolean durable) {
        requireModifiable(name);
        amqpAdmin.declareExchange(type.build(name, durable));
        log.info("Exchange RabbitMQ creado: {} ({}, durable={})", name, type, durable);
    }

    public void deleteExchange(String name) {
        requireModifiable(name);
        amqpAdmin.deleteExchange(name);
        log.info("Exchange RabbitMQ eliminado: {}", name);
    }

    /**
     * Enlaza una cola a un exchange. Se permite enlazar colas propias a los exchanges de
     * ClassFlow (p. ej. para recibir tambien los eventos), pero no tocar las colas reservadas.
     */
    public void createBinding(String exchange, String queue, String routingKey) {
        requireModifiable(queue);
        amqpAdmin.declareBinding(queueBinding(exchange, queue, routingKey));
        log.info("Binding RabbitMQ creado: {} -> {} ({})", exchange, queue, routingKey);
    }

    public void deleteBinding(String exchange, String queue, String routingKey) {
        requireModifiable(queue);
        amqpAdmin.removeBinding(queueBinding(exchange, queue, routingKey));
        log.info("Binding RabbitMQ eliminado: {} -> {} ({})", exchange, queue, routingKey);
    }

    // El binding solo usa nombres, asi que sirve para cualquier tipo de exchange.
    private static Binding queueBinding(String exchange, String queue, String routingKey) {
        return new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }

    private void requireModifiable(String name) {
        if (props.reservedNames().contains(name)) {
            throw new ReservedRabbitResourceException(name);
        }
    }
}
