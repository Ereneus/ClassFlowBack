package com.ohiggins.classflow.analytics.service;

import com.ohiggins.classflow.analytics.dto.AnalyticsSummaryDTO;
import com.ohiggins.classflow.analytics.dto.AttendanceStatsDTO;
import com.ohiggins.classflow.analytics.dto.GradeStatsDTO;
import com.ohiggins.classflow.analytics.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.analytics.kafka.event.GradeEvent;
import com.ohiggins.classflow.analytics.kafka.event.GradeEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Proyeccion en memoria de los eventos academicos (modelo de lectura).
 *
 * <p>El estado se guarda por identificador de entidad ({@code gradeId}, {@code attendanceId}) y
 * cada evento lo reemplaza (upsert), por lo que aplicar dos veces el mismo evento no cambia el
 * resultado: el procesamiento es idempotente y el estado se puede reconstruir releyendo los
 * topicos desde el principio (lo que hace el listener al arrancar). Un evento mas antiguo que
 * el estado conocido se descarta.
 *
 * <p>No conoce Kafka: recibe DTOs ya validados, asi se prueba sin infraestructura.
 */
@Service
public class AcademicAnalyticsService {

    private final Map<Long, GradeEvent> grades = new ConcurrentHashMap<>();
    private final Map<Long, AttendanceEvent> attendance = new ConcurrentHashMap<>();
    private final AtomicLong gradeEventsProcessed = new AtomicLong();
    private final AtomicLong attendanceEventsProcessed = new AtomicLong();
    private final AtomicReference<Instant> lastEventAt = new AtomicReference<>();
    private final double riskThreshold;
    private final int riskMinRecords;

    public AcademicAnalyticsService(
            @Value("${classflow.analytics.attendance-risk-threshold:85}") double riskThreshold,
            @Value("${classflow.analytics.attendance-risk-min-records:3}") int riskMinRecords) {
        this.riskThreshold = riskThreshold;
        this.riskMinRecords = riskMinRecords;
    }

    // ------------------------------------------------------------------ escritura (eventos)

    public void applyGradeEvent(GradeEvent event) {
        if (event.eventType() == GradeEventType.GRADE_DELETED) {
            grades.computeIfPresent(event.gradeId(),
                    (id, current) -> isOlder(event.occurredAt(), current.occurredAt()) ? current : null);
        } else {
            grades.merge(event.gradeId(), event,
                    (current, incoming) -> isOlder(incoming.occurredAt(), current.occurredAt()) ? current : incoming);
        }
        gradeEventsProcessed.incrementAndGet();
        touch(event.occurredAt());
    }

    public void applyAttendanceEvent(AttendanceEvent event) {
        attendance.merge(event.attendanceId(), event,
                (current, incoming) -> isOlder(incoming.occurredAt(), current.occurredAt()) ? current : incoming);
        attendanceEventsProcessed.incrementAndGet();
        touch(event.occurredAt());
    }

    // ------------------------------------------------------------------ lectura (consultas)

    public Optional<GradeStatsDTO> gradeStatsForStudent(Long studentId) {
        return gradeStats(studentId, grades.values().stream()
                .filter(g -> studentId.equals(g.studentId())).toList());
    }

    public Optional<GradeStatsDTO> gradeStatsForSubject(Long subjectId) {
        return gradeStats(subjectId, grades.values().stream()
                .filter(g -> subjectId.equals(g.subjectId())).toList());
    }

    public Optional<AttendanceStatsDTO> attendanceForStudent(Long studentId) {
        return attendanceStats(studentId, attendance.values().stream()
                .filter(a -> studentId.equals(a.studentId())).toList());
    }

    public Optional<AttendanceStatsDTO> attendanceForCourse(Long courseId) {
        return attendanceStats(courseId, attendance.values().stream()
                .filter(a -> courseId.equals(a.courseId())).toList());
    }

    public List<AttendanceStatsDTO> attendanceByCourse() {
        return groupAttendance(AttendanceEvent::courseId).stream()
                .sorted(Comparator.comparing(AttendanceStatsDTO::id))
                .toList();
    }

    public AnalyticsSummaryDTO summary() {
        List<AttendanceStatsDTO> atRisk = groupAttendance(AttendanceEvent::studentId).stream()
                .filter(s -> s.records() >= riskMinRecords && s.attendanceRate() < riskThreshold)
                .sorted(Comparator.comparingDouble(AttendanceStatsDTO::attendanceRate))
                .toList();
        return new AnalyticsSummaryDTO(grades.size(), attendance.size(), gradeEventsProcessed.get(),
                attendanceEventsProcessed.get(), lastEventAt.get(), riskThreshold, atRisk);
    }

    // ------------------------------------------------------------------ calculos

    private static Optional<GradeStatsDTO> gradeStats(Long id, Collection<GradeEvent> items) {
        List<GradeEvent> scored = items.stream().filter(g -> g.score() != null).toList();
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        double average = scored.stream().mapToDouble(GradeEvent::score).average().orElse(0);
        double averagePercentage = scored.stream()
                .filter(g -> g.maxScore() != null && g.maxScore() > 0)
                .mapToDouble(g -> g.score() / g.maxScore() * 100)
                .average().orElse(0);
        double min = scored.stream().mapToDouble(GradeEvent::score).min().orElse(0);
        double max = scored.stream().mapToDouble(GradeEvent::score).max().orElse(0);
        return Optional.of(new GradeStatsDTO(id, scored.size(), round(average), round(averagePercentage),
                min, max));
    }

    private static Optional<AttendanceStatsDTO> attendanceStats(Long id, Collection<AttendanceEvent> items) {
        if (items.isEmpty()) {
            return Optional.empty();
        }
        long present = items.stream().filter(a -> Boolean.TRUE.equals(a.present())).count();
        long absent = items.size() - present;
        long justified = items.stream()
                .filter(a -> !Boolean.TRUE.equals(a.present()))
                .filter(a -> a.justification() != null && !a.justification().isBlank())
                .count();
        double rate = round(present * 100.0 / items.size());
        return Optional.of(new AttendanceStatsDTO(id, items.size(), present, absent, justified, rate));
    }

    private List<AttendanceStatsDTO> groupAttendance(Function<AttendanceEvent, Long> key) {
        return attendance.values().stream()
                .filter(a -> key.apply(a) != null)
                .collect(Collectors.groupingBy(key))
                .entrySet().stream()
                .map(e -> attendanceStats(e.getKey(), e.getValue()))
                .flatMap(Optional::stream)
                .toList();
    }

    private void touch(Instant occurredAt) {
        Instant at = Objects.requireNonNullElseGet(occurredAt, Instant::now);
        lastEventAt.accumulateAndGet(at, (current, incoming) ->
                current == null || incoming.isAfter(current) ? incoming : current);
    }

    private static boolean isOlder(Instant incoming, Instant current) {
        return incoming != null && current != null && incoming.isBefore(current);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
