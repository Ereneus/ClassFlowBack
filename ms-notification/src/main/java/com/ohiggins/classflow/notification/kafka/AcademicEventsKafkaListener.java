package com.ohiggins.classflow.notification.kafka;

import com.ohiggins.classflow.notification.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.notification.kafka.event.GradeEvent;
import com.ohiggins.classflow.notification.service.AcademicEventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Capa de mensajeria: recibe los eventos de los dos topicos, los valida y delega en
 * {@link AcademicEventNotificationService}, que contiene la logica de negocio.
 *
 * <p>Consumer group {@code classflow-notification} (spring.kafka.consumer.group-id) con 3 hilos
 * por topico (spring.kafka.listener.concurrency): cada hilo atiende una de las 3 particiones.
 * Si se levantan mas instancias de ms-notification, Kafka reparte las particiones entre ellas.
 *
 * <p>Errores: cualquier excepcion se propaga al {@code DefaultErrorHandler}
 * (KafkaConsumerConfig), que reintenta con backoff y luego envia el mensaje al DLT. Un payload
 * invalido lanza {@link InvalidKafkaEventException}, que va directo al DLT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AcademicEventsKafkaListener {

    private final KafkaEventValidator validator;
    private final AcademicEventNotificationService notificationHandler;

    @KafkaListener(
            id = "notification-grades",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${classflow.kafka.topics.grades}",
            containerFactory = "gradeEventContainerFactory")
    public void onGradeEvent(ConsumerRecord<String, GradeEvent> record) {
        if (isTombstone(record)) {
            return;
        }
        GradeEvent event = record.value();
        validator.validate(event);
        Optional<Long> notificationId = notificationHandler.handleGradeEvent(event);
        log.info("Kafka: {} {} de la nota {} procesado desde {} -> {}", event.eventType(), event.eventId(),
                event.gradeId(), position(record), result(notificationId));
    }

    @KafkaListener(
            id = "notification-attendance",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${classflow.kafka.topics.attendance}",
            containerFactory = "attendanceEventContainerFactory")
    public void onAttendanceEvent(ConsumerRecord<String, AttendanceEvent> record) {
        if (isTombstone(record)) {
            return;
        }
        AttendanceEvent event = record.value();
        validator.validate(event);
        Optional<Long> notificationId = notificationHandler.handleAttendanceEvent(event);
        log.info("Kafka: {} {} de la asistencia {} procesado desde {} -> {}", event.eventType(), event.eventId(),
                event.attendanceId(), position(record), result(notificationId));
    }

    /**
     * En un topico compactado, un valor null (tombstone) marca la key como borrada. No es un
     * error ni requiere notificar: se ignora.
     */
    private static boolean isTombstone(ConsumerRecord<String, ?> record) {
        if (record.value() == null) {
            log.debug("Kafka: tombstone ignorado en {}", position(record));
            return true;
        }
        return false;
    }

    private static String position(ConsumerRecord<String, ?> record) {
        return record.topic() + "-" + record.partition() + "@" + record.offset();
    }

    private static String result(Optional<Long> notificationId) {
        return notificationId.map(id -> "notificacion " + id).orElse("sin notificacion");
    }
}
