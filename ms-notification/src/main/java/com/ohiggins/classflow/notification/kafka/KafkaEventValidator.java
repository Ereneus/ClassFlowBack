package com.ohiggins.classflow.notification.kafka;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida el payload de los eventos Kafka con las anotaciones de Bean Validation de cada DTO.
 */
@Component
@RequiredArgsConstructor
public class KafkaEventValidator {

    private final Validator validator;

    public <T> void validate(T event) {
        if (event == null) {
            throw new InvalidKafkaEventException("evento vacio");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidKafkaEventException(event.getClass().getSimpleName() + " invalido: " + detail);
        }
    }
}
