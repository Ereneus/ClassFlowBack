package com.ohiggins.classflow.analytics.dto;

/**
 * Estadisticas de calificaciones de un estudiante o de una asignatura.
 *
 * @param id                estudiante o asignatura.
 * @param grades            cantidad de notas vigentes.
 * @param average           promedio simple de las notas.
 * @param averagePercentage promedio del porcentaje de logro (nota / nota maxima * 100).
 * @param min               nota mas baja.
 * @param max               nota mas alta.
 */
public record GradeStatsDTO(Long id, long grades, double average, double averagePercentage, double min, double max) {
}
