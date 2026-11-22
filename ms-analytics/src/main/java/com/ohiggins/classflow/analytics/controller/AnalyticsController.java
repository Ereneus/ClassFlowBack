package com.ohiggins.classflow.analytics.controller;

import com.ohiggins.classflow.analytics.dto.AnalyticsSummaryDTO;
import com.ohiggins.classflow.analytics.dto.AttendanceStatsDTO;
import com.ohiggins.classflow.analytics.dto.GradeStatsDTO;
import com.ohiggins.classflow.analytics.service.AcademicAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indicadores calculados en streaming a partir de los eventos Kafka.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analitica", description = "Indicadores academicos calculados desde Kafka")
public class AnalyticsController {

    private final AcademicAnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Resumen general y estudiantes con asistencia bajo el umbral")
    public AnalyticsSummaryDTO summary() {
        return analyticsService.summary();
    }

    @GetMapping("/students/{studentId}/grades")
    @Operation(summary = "Promedio, minimo y maximo de notas de un estudiante")
    public ResponseEntity<GradeStatsDTO> studentGrades(@PathVariable Long studentId) {
        return ResponseEntity.of(analyticsService.gradeStatsForStudent(studentId));
    }

    @GetMapping("/students/{studentId}/attendance")
    @Operation(summary = "Porcentaje de asistencia de un estudiante")
    public ResponseEntity<AttendanceStatsDTO> studentAttendance(@PathVariable Long studentId) {
        return ResponseEntity.of(analyticsService.attendanceForStudent(studentId));
    }

    @GetMapping("/subjects/{subjectId}/grades")
    @Operation(summary = "Promedio de notas de una asignatura")
    public ResponseEntity<GradeStatsDTO> subjectGrades(@PathVariable Long subjectId) {
        return ResponseEntity.of(analyticsService.gradeStatsForSubject(subjectId));
    }

    @GetMapping("/courses/attendance")
    @Operation(summary = "Porcentaje de asistencia de todos los cursos")
    public List<AttendanceStatsDTO> attendanceByCourse() {
        return analyticsService.attendanceByCourse();
    }

    @GetMapping("/courses/{courseId}/attendance")
    @Operation(summary = "Porcentaje de asistencia de un curso")
    public ResponseEntity<AttendanceStatsDTO> courseAttendance(@PathVariable Long courseId) {
        return ResponseEntity.of(analyticsService.attendanceForCourse(courseId));
    }
}
