package com.ohiggins.classflow.assistance.messaging;

import com.ohiggins.classflow.assistance.event.AttendanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los registros de asistencia en el topico {@code classflow.assistance.attendance}.
 *
 * <p>Es best-effort: la asistencia ya quedo guardada, asi que si el cluster no responde se
 * registra el error y la peticion HTTP no falla. El envio es asincrono y el resultado
 * (particion/offset o error) se registra en el callback.
 */
@Component
@Slf4j
public class AttendanceEventProducer {

    private final KafkaTemplate<String, AttendanceEvent> kafkaTemplate;
    private final String topic;

    public AttendanceEventProducer(KafkaTemplate<String, AttendanceEvent> kafkaTemplate,
                                   @Value("${classflow.kafka.topics.attendance}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(AttendanceEvent event) {
        String key = String.valueOf(event.studentId());
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka: no se pudo publicar {} de la asistencia {} (evento {}) en {}",
                            event.eventType(), event.attendanceId(), event.eventId(), topic, ex);
                    return;
                }
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Kafka: {} de la asistencia {} publicado en {}-{} offset {} (evento {})",
                        event.eventType(), event.attendanceId(), metadata.topic(), metadata.partition(),
                        metadata.offset(), event.eventId());
            });
        } catch (RuntimeException e) {
            // Errores sincronos: serializacion, metadata no disponible antes de max.block.ms, etc.
            log.error("Kafka: error al enviar {} de la asistencia {} (evento {}) a {}",
                    event.eventType(), event.attendanceId(), event.eventId(), topic, e);
        }
    }
}
