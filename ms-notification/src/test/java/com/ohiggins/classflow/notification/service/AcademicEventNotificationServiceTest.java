package com.ohiggins.classflow.notification.service;

import com.ohiggins.classflow.notification.dto.AlertRequestDTO;
import com.ohiggins.classflow.notification.dto.NotificationResponseDTO;
import com.ohiggins.classflow.notification.enums.NotificationType;
import com.ohiggins.classflow.notification.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.notification.kafka.event.AttendanceEventType;
import com.ohiggins.classflow.notification.kafka.event.GradeEvent;
import com.ohiggins.classflow.notification.kafka.event.GradeEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicEventNotificationService Tests")
class AcademicEventNotificationServiceTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AcademicEventNotificationService service;

    private static GradeEvent grade(GradeEventType type) {
        return new GradeEvent("e-1", type, 7L, 101L, 3L, "Prueba 1", 2L, 6.5, 7.0, Instant.now());
    }

    private static AttendanceEvent attendance(boolean present, String justification) {
        return new AttendanceEvent("e-2", AttendanceEventType.ATTENDANCE_REGISTERED, 9L, 101L, 4L,
                LocalDate.of(2026, 11, 20), present, justification, Instant.now());
    }

    private void notificationServiceReturnsId(long id) {
        NotificationResponseDTO created = new NotificationResponseDTO();
        created.setId(id);
        when(notificationService.createNotification(any())).thenReturn(created);
    }

    @Test
    @DisplayName("Nota creada notifica al estudiante con la nota y el maximo")
    void gradeCreatedNotifiesStudent() {
        notificationServiceReturnsId(55L);

        Optional<Long> result = service.handleGradeEvent(grade(GradeEventType.GRADE_CREATED));

        assertThat(result).contains(55L);
        ArgumentCaptor<AlertRequestDTO> alert = ArgumentCaptor.forClass(AlertRequestDTO.class);
        verify(notificationService).createNotification(alert.capture());
        assertThat(alert.getValue().getUserId()).isEqualTo(101L);
        assertThat(alert.getValue().getType()).isEqualTo(NotificationType.ALERT);
        assertThat(alert.getValue().getSubject()).isEqualTo("Nueva calificacion: Prueba 1");
        assertThat(alert.getValue().getContent()).isEqualTo("Obtuviste 6.5 de 7 en Prueba 1.");
    }

    @Test
    @DisplayName("Nota corregida usa un asunto distinto")
    void gradeUpdatedUsesCorrectionSubject() {
        notificationServiceReturnsId(56L);

        service.handleGradeEvent(grade(GradeEventType.GRADE_UPDATED));

        ArgumentCaptor<AlertRequestDTO> alert = ArgumentCaptor.forClass(AlertRequestDTO.class);
        verify(notificationService).createNotification(alert.capture());
        assertThat(alert.getValue().getSubject()).startsWith("Calificacion corregida");
    }

    @Test
    @DisplayName("Nota eliminada no genera notificacion")
    void gradeDeletedDoesNotNotify() {
        assertThat(service.handleGradeEvent(grade(GradeEventType.GRADE_DELETED))).isEmpty();
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Asistencia presente no genera notificacion")
    void presentDoesNotNotify() {
        assertThat(service.handleAttendanceEvent(attendance(true, null))).isEmpty();
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Ausencia sin justificar pide presentar justificativo")
    void unjustifiedAbsenceNotifies() {
        notificationServiceReturnsId(57L);

        assertThat(service.handleAttendanceEvent(attendance(false, " "))).contains(57L);

        ArgumentCaptor<AlertRequestDTO> alert = ArgumentCaptor.forClass(AlertRequestDTO.class);
        verify(notificationService).createNotification(alert.capture());
        assertThat(alert.getValue().getSubject()).isEqualTo("Inasistencia registrada el 20-11-2026");
        assertThat(alert.getValue().getContent()).contains("sin justificacion");
    }

    @Test
    @DisplayName("Ausencia justificada incluye la justificacion")
    void justifiedAbsenceIncludesJustification() {
        notificationServiceReturnsId(58L);

        service.handleAttendanceEvent(attendance(false, "Certificado medico"));

        ArgumentCaptor<AlertRequestDTO> alert = ArgumentCaptor.forClass(AlertRequestDTO.class);
        verify(notificationService).createNotification(alert.capture());
        assertThat(alert.getValue().getContent()).contains("Certificado medico");
    }
}
