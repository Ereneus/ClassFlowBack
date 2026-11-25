package com.ohiggins.classflow.analytics.service;

import com.ohiggins.classflow.analytics.dto.AnalyticsSummaryDTO;
import com.ohiggins.classflow.analytics.dto.AttendanceStatsDTO;
import com.ohiggins.classflow.analytics.dto.GradeStatsDTO;
import com.ohiggins.classflow.analytics.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.analytics.kafka.event.AttendanceEventType;
import com.ohiggins.classflow.analytics.kafka.event.GradeEvent;
import com.ohiggins.classflow.analytics.kafka.event.GradeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AcademicAnalyticsService Tests")
class AcademicAnalyticsServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private AcademicAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AcademicAnalyticsService(85, 3);
    }

    private static GradeEvent grade(GradeEventType type, long gradeId, long studentId, double score, Instant at) {
        return new GradeEvent("g-" + gradeId + "-" + at, type, gradeId, studentId, 1L, "Prueba", 10L, score, 7.0, at);
    }

    private static AttendanceEvent attendance(long id, long studentId, long courseId, boolean present, String justification) {
        return new AttendanceEvent("a-" + id, AttendanceEventType.ATTENDANCE_REGISTERED, id, studentId, courseId,
                LocalDate.of(2026, 11, (int) id), present, justification, T0.plusSeconds(id));
    }

    @Test
    @DisplayName("Calcula promedio, porcentaje de logro, minimo y maximo por estudiante")
    void gradeStatsPerStudent() {
        service.applyGradeEvent(grade(GradeEventType.GRADE_CREATED, 1, 100, 7.0, T0));
        service.applyGradeEvent(grade(GradeEventType.GRADE_CREATED, 2, 100, 3.5, T0));
        service.applyGradeEvent(grade(GradeEventType.GRADE_CREATED, 3, 200, 6.0, T0));

        GradeStatsDTO stats = service.gradeStatsForStudent(100L).orElseThrow();

        assertThat(stats.grades()).isEqualTo(2);
        assertThat(stats.average()).isEqualTo(5.25);
        assertThat(stats.averagePercentage()).isEqualTo(75.0);
        assertThat(stats.min()).isEqualTo(3.5);
        assertThat(stats.max()).isEqualTo(7.0);
        assertThat(service.gradeStatsForSubject(10L).orElseThrow().grades()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una correccion reemplaza la nota y una eliminacion la quita")
    void updateReplacesAndDeleteRemoves() {
        service.applyGradeEvent(grade(GradeEventType.GRADE_CREATED, 1, 100, 4.0, T0));
        service.applyGradeEvent(grade(GradeEventType.GRADE_UPDATED, 1, 100, 6.0, T0.plusSeconds(60)));

        assertThat(service.gradeStatsForStudent(100L).orElseThrow().average()).isEqualTo(6.0);

        service.applyGradeEvent(grade(GradeEventType.GRADE_DELETED, 1, 100, 6.0, T0.plusSeconds(120)));

        assertThat(service.gradeStatsForStudent(100L)).isEmpty();
    }

    @Test
    @DisplayName("Reprocesar eventos (reintentos o relectura del topico) no altera el resultado")
    void processingIsIdempotent() {
        GradeEvent created = grade(GradeEventType.GRADE_CREATED, 1, 100, 5.0, T0);
        service.applyGradeEvent(created);
        service.applyGradeEvent(created);

        AttendanceEvent absent = attendance(1, 100, 5, false, null);
        service.applyAttendanceEvent(absent);
        service.applyAttendanceEvent(absent);

        assertThat(service.gradeStatsForStudent(100L).orElseThrow().grades()).isEqualTo(1);
        assertThat(service.attendanceForStudent(100L).orElseThrow().records()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un evento mas antiguo que el estado conocido se descarta")
    void olderEventIsIgnored() {
        service.applyGradeEvent(grade(GradeEventType.GRADE_UPDATED, 1, 100, 6.0, T0.plusSeconds(60)));
        service.applyGradeEvent(grade(GradeEventType.GRADE_CREATED, 1, 100, 2.0, T0));

        assertThat(service.gradeStatsForStudent(100L).orElseThrow().average()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("Calcula asistencia por curso y detecta estudiantes en riesgo")
    void attendanceAndRisk() {
        // Estudiante 100: 1 de 4 presente (25 %) -> en riesgo
        service.applyAttendanceEvent(attendance(1, 100, 5, true, null));
        service.applyAttendanceEvent(attendance(2, 100, 5, false, "Certificado"));
        service.applyAttendanceEvent(attendance(3, 100, 5, false, null));
        service.applyAttendanceEvent(attendance(4, 100, 5, false, null));
        // Estudiante 200: 3 de 3 presente -> sin riesgo
        service.applyAttendanceEvent(attendance(5, 200, 5, true, null));
        service.applyAttendanceEvent(attendance(6, 200, 5, true, null));
        service.applyAttendanceEvent(attendance(7, 200, 5, true, null));
        // Estudiante 300: 0 de 1 -> pocos registros, no se reporta
        service.applyAttendanceEvent(attendance(8, 300, 6, false, null));

        AttendanceStatsDTO course5 = service.attendanceForCourse(5L).orElseThrow();
        assertThat(course5.records()).isEqualTo(7);
        assertThat(course5.present()).isEqualTo(4);
        assertThat(course5.justifiedAbsences()).isEqualTo(1);
        assertThat(course5.attendanceRate()).isEqualTo(57.14);
        assertThat(service.attendanceByCourse()).extracting(AttendanceStatsDTO::id).containsExactly(5L, 6L);

        AnalyticsSummaryDTO summary = service.summary();
        assertThat(summary.attendanceRecordsTracked()).isEqualTo(8);
        assertThat(summary.attendanceEventsProcessed()).isEqualTo(8);
        assertThat(summary.studentsAtRisk()).extracting(AttendanceStatsDTO::id).containsExactly(100L);
        assertThat(summary.lastEventAt()).isEqualTo(T0.plusSeconds(8));
    }
}
