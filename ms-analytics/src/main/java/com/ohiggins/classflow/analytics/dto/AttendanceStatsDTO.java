package com.ohiggins.classflow.analytics.dto;

/**
 * Estadisticas de asistencia de un estudiante o de un curso.
 *
 * @param id                 estudiante o curso.
 * @param records            registros de asistencia.
 * @param present            dias presentes.
 * @param absent             dias ausentes.
 * @param justifiedAbsences  ausencias con justificacion.
 * @param attendanceRate     porcentaje de asistencia (0-100).
 */
public record AttendanceStatsDTO(Long id, long records, long present, long absent, long justifiedAbsences,
                                 double attendanceRate) {
}
