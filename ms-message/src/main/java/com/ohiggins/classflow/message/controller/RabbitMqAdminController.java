package com.ohiggins.classflow.message.controller;

import com.ohiggins.classflow.message.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Administracion basica de colas, exchanges y bindings (solo administradores, ver SecurityConfig).
 * Los recursos de los que depende el flujo de mensajes estan reservados y no se pueden modificar.
 */
@RestController
@RequestMapping("/api/rabbitmq")
@RequiredArgsConstructor
public class RabbitMqAdminController {

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9._:-]{1,255}$");
    private static final Set<String> RESERVED_NAMES = Set.of(
            RabbitMqConfig.EVENTS_EXCHANGE,
            RabbitMqConfig.DEAD_LETTER_EXCHANGE,
            RabbitMqConfig.MESSAGE_CREATED_QUEUE,
            RabbitMqConfig.DEAD_LETTER_QUEUE
    );

    private final AmqpAdmin amqpAdmin;

    @PostMapping("/queues/{name}")
    public ResponseEntity<Void> createQueue(@PathVariable String name, @RequestParam(defaultValue = "true") boolean durable) {
        requireModifiable(name);
        Queue queue = durable
                ? QueueBuilder.durable(name).build()
                : QueueBuilder.nonDurable(name).build();
        amqpAdmin.declareQueue(queue);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(@PathVariable String name) {
        requireModifiable(name);
        amqpAdmin.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges/{name}")
    public ResponseEntity<Void> createExchange(
            @PathVariable String name,
            @RequestParam(defaultValue = "topic") String type,
            @RequestParam(defaultValue = "true") boolean durable) {
        requireModifiable(name);
        Exchange exchange = switch (type.toLowerCase()) {
            case "topic" -> new TopicExchange(name, durable, false);
            case "direct" -> new DirectExchange(name, durable, false);
            case "fanout" -> new FanoutExchange(name, durable, false);
            case "headers" -> new HeadersExchange(name, durable, false);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de exchange no soportado: " + type + " (topic, direct, fanout o headers)");
        };
        amqpAdmin.declareExchange(exchange);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(@PathVariable String name) {
        requireModifiable(name);
        amqpAdmin.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Enlaza una cola a un exchange. Se permite enlazar colas propias a los exchanges de
     * ClassFlow (p. ej. para recibir tambien los eventos), pero no tocar las colas reservadas.
     */
    @PostMapping("/bindings/{exchange}/{queue}")
    public ResponseEntity<Void> createBinding(
            @PathVariable String exchange,
            @PathVariable String queue,
            @RequestParam(defaultValue = "") String routingKey) {
        requireValidName(exchange);
        requireModifiable(queue);
        amqpAdmin.declareBinding(queueBinding(exchange, queue, routingKey));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/bindings/{exchange}/{queue}")
    public ResponseEntity<Void> deleteBinding(
            @PathVariable String exchange,
            @PathVariable String queue,
            @RequestParam(defaultValue = "") String routingKey) {
        requireValidName(exchange);
        requireModifiable(queue);
        amqpAdmin.removeBinding(queueBinding(exchange, queue, routingKey));
        return ResponseEntity.noContent().build();
    }

    // El binding solo usa nombres, asi que sirve para cualquier tipo de exchange.
    private static Binding queueBinding(String exchange, String queue, String routingKey) {
        return new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }

    private static void requireModifiable(String name) {
        requireValidName(name);
        if (RESERVED_NAMES.contains(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El recurso " + name + " es usado por ClassFlow y no se puede modificar");
        }
    }

    private static void requireValidName(String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre invalido: solo letras, numeros y . _ : - (maximo 255)");
        }
    }
}
