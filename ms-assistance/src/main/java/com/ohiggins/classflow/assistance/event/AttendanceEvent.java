package com.ohiggins.classflow.assistance.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento publicado en el topico {@code classflow.assistance.attendance} cuando se registra o
 * corrige una asistencia. Es el contrato (DTO) que leen ms-notification y ms-analytics.
 *
 * <p>La key del mensaje es {@code studentId}: los eventos de un mismo estudiante quedan en la
 * misma particion y se procesan en orden.
 *
 * @param eventId       identificador unico del evento (idempotencia en los consumidores).
 * @param eventType     tipo de cambio.
 * @param attendanceId  registro de asistencia afectado.
 * @param studentId     estudiante.
 * @param courseId      curso.
 * @param date          dia de clases.
 * @param present       si el estudiante asistio.
 * @param justification justificacion de la ausencia, si existe.
 * @param occurredAt    instante en que ocurrio el cambio.
 */
public record AttendanceEvent(
        String eventId,
        AttendanceEventType eventType,
        Long attendanceId,
        Long studentId,
        Long courseId,
        LocalDate date,
        Boolean present,
        String justification,
        Instant occurredAt
) {

    public static AttendanceEvent of(AttendanceEventType type, Long attendanceId, Long studentId, Long courseId,
                                     LocalDate date, Boolean present, String justification) {
        return new AttendanceEvent(UUID.randomUUID().toString(), type, attendanceId, studentId, courseId,
                date, present, justification, Instant.now());
    }
}
