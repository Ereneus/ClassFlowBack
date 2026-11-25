package com.ohiggins.classflow.academic.messaging;

import com.ohiggins.classflow.academic.event.GradeEvent;
import com.ohiggins.classflow.academic.event.GradeEventType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GradeEventProducer Tests")
class GradeEventProducerTest {

    private static final String TOPIC = "classflow.academic.grades";

    @Mock
    private KafkaTemplate<String, GradeEvent> kafkaTemplate;

    private GradeEventProducer producer;
    private GradeEvent event;

    @BeforeEach
    void setUp() {
        producer = new GradeEventProducer(kafkaTemplate, TOPIC);
        event = GradeEvent.of(GradeEventType.GRADE_CREATED, 7L, 101L, 1L, "Prueba 1", 2L, 6.5, 7.0);
    }

    @Test
    @DisplayName("Publica en el topico de notas usando gradeId como key")
    void publishesWithGradeIdAsKey() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 1), 42L, 0, 0L, 0, 0);
        SendResult<String, GradeEvent> result = new SendResult<>(new ProducerRecord<>(TOPIC, "7", event), metadata);
        when(kafkaTemplate.send(TOPIC, "7", event)).thenReturn(CompletableFuture.completedFuture(result));

        producer.publish(event);

        verify(kafkaTemplate).send(TOPIC, "7", event);
    }

    @Test
    @DisplayName("Un fallo asincrono del broker no se propaga al servicio")
    void asyncFailureIsOnlyLogged() {
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker caido")));

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un fallo sincrono (p. ej. metadata no disponible) no se propaga al servicio")
    void syncFailureIsOnlyLogged() {
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any())).thenThrow(new RuntimeException("timeout"));

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();
    }
}
