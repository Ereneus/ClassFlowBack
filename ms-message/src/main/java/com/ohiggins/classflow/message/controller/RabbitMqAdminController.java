package com.ohiggins.classflow.message.controller;

import com.ohiggins.classflow.message.service.ExchangeType;
import com.ohiggins.classflow.message.service.RabbitAdminService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de administracion de RabbitMQ (solo administradores, ver SecurityConfig).
 * Valida los parametros de entrada y delega la logica en {@link RabbitAdminService}.
 */
@RestController
@RequestMapping("/api/rabbitmq")
@RequiredArgsConstructor
public class RabbitMqAdminController {

    private static final String NAME_REGEX = "^[A-Za-z0-9._:-]+$";
    private static final String NAME_MESSAGE = "solo letras, numeros y . _ : - (maximo 255)";
    private static final String ROUTING_KEY_REGEX = "^[A-Za-z0-9._:*#-]*$";

    private final RabbitAdminService rabbitAdminService;

    @PostMapping("/queues/{name}")
    public ResponseEntity<Void> createQueue(
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String name,
            @RequestParam(defaultValue = "true") boolean durable) {
        rabbitAdminService.createQueue(name, durable);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String name) {
        rabbitAdminService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges/{name}")
    public ResponseEntity<Void> createExchange(
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String name,
            @RequestParam(defaultValue = "topic")
            @Pattern(regexp = ExchangeType.PATTERN, message = "debe ser topic, direct, fanout o headers") String type,
            @RequestParam(defaultValue = "true") boolean durable) {
        rabbitAdminService.createExchange(name, ExchangeType.from(type), durable);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String name) {
        rabbitAdminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings/{exchange}/{queue}")
    public ResponseEntity<Void> createBinding(
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String exchange,
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String queue,
            @RequestParam(defaultValue = "") @Size(max = 255)
            @Pattern(regexp = ROUTING_KEY_REGEX, message = "solo letras, numeros y . _ : * # -") String routingKey) {
        rabbitAdminService.createBinding(exchange, queue, routingKey);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/bindings/{exchange}/{queue}")
    public ResponseEntity<Void> deleteBinding(
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String exchange,
            @PathVariable @NotBlank @Size(max = 255) @Pattern(regexp = NAME_REGEX, message = NAME_MESSAGE) String queue,
            @RequestParam(defaultValue = "") @Size(max = 255)
            @Pattern(regexp = ROUTING_KEY_REGEX, message = "solo letras, numeros y . _ : * # -") String routingKey) {
        rabbitAdminService.deleteBinding(exchange, queue, routingKey);
        return ResponseEntity.noContent().build();
    }
}
