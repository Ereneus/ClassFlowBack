package com.ohiggins.classflow.analytics.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resumen general del estado analitico reconstruido desde Kafka.
 *
 * @param gradesTracked             notas vigentes conocidas.
 * @param attendanceRecordsTracked  registros de asistencia conocidos.
 * @param gradeEventsProcessed      eventos de notas procesados desde el arranque.
 * @param attendanceEventsProcessed eventos de asistencia procesados desde el arranque.
 * @param lastEventAt               instante del ultimo evento procesado.
 * @param attendanceThreshold       umbral de asistencia usado para detectar riesgo.
 * @param studentsAtRisk            estudiantes bajo el umbral de asistencia, de peor a mejor.
 */
public record AnalyticsSummaryDTO(
        long gradesTracked,
        long attendanceRecordsTracked,
        long gradeEventsProcessed,
        long attendanceEventsProcessed,
        Instant lastEventAt,
        double attendanceThreshold,
        List<AttendanceStatsDTO> studentsAtRisk
) {
}
