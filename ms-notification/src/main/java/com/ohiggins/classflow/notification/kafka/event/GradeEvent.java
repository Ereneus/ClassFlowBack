package com.ohiggins.classflow.notification.kafka.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * Copia local del contrato que publica ms-academic en {@code classflow.academic.grades}.
 * Las anotaciones de validacion definen que es un payload aceptable: un evento que no las
 * cumple se considera defectuoso, no se reintenta y va directo al DLT.
 */
public record GradeEvent(
        @NotBlank String eventId,
        @NotNull GradeEventType eventType,
        @NotNull Long gradeId,
        @NotNull Long studentId,
        Long evaluationId,
        String evaluationName,
        Long subjectId,
        @PositiveOrZero Double score,
        @PositiveOrZero Double maxScore,
        @NotNull Instant occurredAt
) {
}
