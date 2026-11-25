package com.ohiggins.classflow.analytics.kafka;

import com.ohiggins.classflow.analytics.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.analytics.kafka.event.GradeEvent;
import com.ohiggins.classflow.analytics.service.AcademicAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Capa de mensajeria de ms-analytics. Consume los mismos topicos que ms-notification pero en
 * otro consumer group ({@code classflow-analytics}): cada grupo recibe todos los eventos y
 * avanza con sus propios offsets, asi notificar y calcular indicadores son responsabilidades
 * independientes que no se bloquean entre si.
 *
 * <p>Como la proyeccion vive en memoria, al asignarse las particiones se relee cada topico
 * desde el principio ({@link AbstractConsumerSeekAware}) para reconstruir el estado. El topico
 * de notas es compactado: conserva la ultima version de cada nota, lo que acota esa relectura.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsKafkaListener extends AbstractConsumerSeekAware {

    private final KafkaEventValidator validator;
    private final AcademicAnalyticsService analyticsService;

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        super.onPartitionsAssigned(assignments, callback);
        log.info("Kafka: particiones asignadas {}; releyendo desde el inicio para reconstruir la proyeccion",
                assignments.keySet());
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(
            id = "analytics-grades",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${classflow.kafka.topics.grades}",
            containerFactory = "gradeEventContainerFactory")
    public void onGradeEvent(ConsumerRecord<String, GradeEvent> record) {
        if (record.value() == null) {
            // Tombstone del topico compactado: la nota ya no existe en la fuente.
            log.debug("Kafka: tombstone ignorado en {}", position(record));
            return;
        }
        GradeEvent event = record.value();
        validator.validate(event);
        analyticsService.applyGradeEvent(event);
        log.debug("Kafka: {} {} aplicado desde {}", event.eventType(), event.eventId(), position(record));
    }

    @KafkaListener(
            id = "analytics-attendance",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${classflow.kafka.topics.attendance}",
            containerFactory = "attendanceEventContainerFactory")
    public void onAttendanceEvent(ConsumerRecord<String, AttendanceEvent> record) {
        if (record.value() == null) {
            log.debug("Kafka: tombstone ignorado en {}", position(record));
            return;
        }
        AttendanceEvent event = record.value();
        validator.validate(event);
        analyticsService.applyAttendanceEvent(event);
        log.debug("Kafka: {} {} aplicado desde {}", event.eventType(), event.eventId(), position(record));
    }

    private static String position(ConsumerRecord<String, ?> record) {
        return record.topic() + "-" + record.partition() + "@" + record.offset();
    }
}
