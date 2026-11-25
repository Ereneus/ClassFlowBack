package com.ohiggins.classflow.notification.service;

import com.ohiggins.classflow.notification.dto.AlertRequestDTO;
import com.ohiggins.classflow.notification.enums.NotificationType;
import com.ohiggins.classflow.notification.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.notification.kafka.event.GradeEvent;
import com.ohiggins.classflow.notification.kafka.event.GradeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Reglas de negocio que convierten eventos academicos en notificaciones para el estudiante.
 * No sabe nada de Kafka: recibe DTOs ya validados desde la capa de mensajeria, lo que permite
 * probarla y reutilizarla sin infraestructura.
 *
 * <ul>
 *   <li>Nota creada o corregida: se avisa al estudiante con la nota obtenida.</li>
 *   <li>Nota eliminada: no se notifica.</li>
 *   <li>Asistencia con ausencia: se avisa al estudiante (indicando si esta justificada).</li>
 *   <li>Asistencia presente: no se notifica.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicEventNotificationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final NotificationService notificationService;

    /**
     * @return id de la notificacion creada, o vacio si el evento no requiere notificar.
     */
    public Optional<Long> handleGradeEvent(GradeEvent event) {
        if (event.eventType() == GradeEventType.GRADE_DELETED) {
            log.debug("Nota {} eliminada: no se notifica", event.gradeId());
            return Optional.empty();
        }

        String evaluation = event.evaluationName() != null ? event.evaluationName() : "evaluacion " + event.evaluationId();
        boolean updated = event.eventType() == GradeEventType.GRADE_UPDATED;

        AlertRequestDTO alert = new AlertRequestDTO();
        alert.setUserId(event.studentId());
        alert.setType(NotificationType.ALERT);
        alert.setSubject((updated ? "Calificacion corregida: " : "Nueva calificacion: ") + evaluation);
        alert.setContent(String.format(Locale.ROOT, "Obtuviste %s%s en %s.",
                formatScore(event.score()),
                event.maxScore() != null ? " de " + formatScore(event.maxScore()) : "",
                evaluation));

        return Optional.of(notificationService.createNotification(alert).getId());
    }

    /**
     * @return id de la notificacion creada, o vacio si el evento no requiere notificar.
     */
    public Optional<Long> handleAttendanceEvent(AttendanceEvent event) {
        if (Boolean.TRUE.equals(event.present())) {
            log.debug("Asistencia {} presente: no se notifica", event.attendanceId());
            return Optional.empty();
        }

        boolean justified = event.justification() != null && !event.justification().isBlank();

        AlertRequestDTO alert = new AlertRequestDTO();
        alert.setUserId(event.studentId());
        alert.setType(NotificationType.ALERT);
        alert.setSubject("Inasistencia registrada el " + event.date().format(DATE_FORMAT));
        alert.setContent(justified
                ? "Se registro tu inasistencia del " + event.date().format(DATE_FORMAT)
                        + " con la justificacion: " + event.justification()
                : "Se registro tu inasistencia del " + event.date().format(DATE_FORMAT)
                        + " sin justificacion. Si corresponde, presenta tu justificativo.");

        return Optional.of(notificationService.createNotification(alert).getId());
    }

    private static String formatScore(Double score) {
        if (score == null) {
            return "-";
        }
        return score % 1 == 0 ? String.valueOf(score.longValue()) : String.valueOf(score);
    }
}
