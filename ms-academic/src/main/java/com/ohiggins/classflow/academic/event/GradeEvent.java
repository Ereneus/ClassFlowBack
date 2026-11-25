package com.ohiggins.classflow.academic.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado en el topico {@code classflow.academic.grades} cada vez que una
 * calificacion cambia. Es el contrato (DTO) compartido con los consumidores
 * (ms-notification, ms-analytics), que declaran su propia copia del record.
 *
 * <p>La key del mensaje es {@code gradeId}: todas las versiones de una misma nota caen en la
 * misma particion (orden garantizado) y el topico compactado conserva la ultima.
 *
 * @param eventId        identificador unico del evento (idempotencia en los consumidores).
 * @param eventType      tipo de cambio sobre la calificacion.
 * @param gradeId        calificacion afectada.
 * @param studentId      estudiante calificado.
 * @param evaluationId   evaluacion a la que pertenece la nota.
 * @param evaluationName nombre de la evaluacion (evita que el consumidor consulte ms-academic).
 * @param subjectId      asignatura de la evaluacion.
 * @param score          nota obtenida.
 * @param maxScore       nota maxima de la evaluacion.
 * @param occurredAt     instante en que ocurrio el cambio.
 */
public record GradeEvent(
        String eventId,
        GradeEventType eventType,
        Long gradeId,
        Long studentId,
        Long evaluationId,
        String evaluationName,
        Long subjectId,
        Double score,
        Double maxScore,
        Instant occurredAt
) {

    public static GradeEvent of(GradeEventType type, Long gradeId, Long studentId, Long evaluationId,
                                String evaluationName, Long subjectId, Double score, Double maxScore) {
        return new GradeEvent(UUID.randomUUID().toString(), type, gradeId, studentId, evaluationId,
                evaluationName, subjectId, score, maxScore, Instant.now());
    }
}
