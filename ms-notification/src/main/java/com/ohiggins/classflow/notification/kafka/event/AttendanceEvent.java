package com.ohiggins.classflow.notification.kafka.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Copia local del contrato que publica ms-assistance en {@code classflow.assistance.attendance}.
 * Un evento que no cumple las validaciones no se reintenta y va directo al DLT.
 */
public record AttendanceEvent(
        @NotBlank String eventId,
        @NotNull AttendanceEventType eventType,
        @NotNull Long attendanceId,
        @NotNull Long studentId,
        @NotNull Long courseId,
        @NotNull LocalDate date,
        @NotNull Boolean present,
        String justification,
        @NotNull Instant occurredAt
) {
}
