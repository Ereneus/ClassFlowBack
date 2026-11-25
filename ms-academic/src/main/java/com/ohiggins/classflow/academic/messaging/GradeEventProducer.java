package com.ohiggins.classflow.academic.messaging;

import com.ohiggins.classflow.academic.event.GradeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los cambios de calificaciones en el topico {@code classflow.academic.grades}.
 *
 * <p>Es best-effort, igual que el productor RabbitMQ de ms-message: la nota ya quedo guardada
 * en la base de datos, asi que si el cluster no responde se registra el error y la peticion
 * HTTP no falla. El envio es asincrono; el resultado (particion/offset o error) se registra
 * en el callback.
 */
@Component
@Slf4j
public class GradeEventProducer {

    private final KafkaTemplate<String, GradeEvent> kafkaTemplate;
    private final String topic;

    public GradeEventProducer(KafkaTemplate<String, GradeEvent> kafkaTemplate,
                              @Value("${classflow.kafka.topics.grades}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(GradeEvent event) {
        String key = String.valueOf(event.gradeId());
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka: no se pudo publicar {} de la nota {} (evento {}) en {}",
                            event.eventType(), event.gradeId(), event.eventId(), topic, ex);
                    return;
                }
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Kafka: {} de la nota {} publicado en {}-{} offset {} (evento {})",
                        event.eventType(), event.gradeId(), metadata.topic(), metadata.partition(),
                        metadata.offset(), event.eventId());
            });
        } catch (RuntimeException e) {
            // Errores sincronos: serializacion, metadata no disponible antes de max.block.ms, etc.
            log.error("Kafka: error al enviar {} de la nota {} (evento {}) a {}",
                    event.eventType(), event.gradeId(), event.eventId(), topic, e);
        }
    }
}
