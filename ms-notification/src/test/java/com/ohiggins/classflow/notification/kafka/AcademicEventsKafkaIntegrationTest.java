package com.ohiggins.classflow.notification.kafka;

import com.ohiggins.classflow.notification.entity.Notification;
import com.ohiggins.classflow.notification.repository.NotificationRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Prueba de punta a punta contra un broker Kafka embebido: los eventos validos generan
 * notificaciones en la base de datos y los defectuosos terminan en el DLT sin detener al
 * consumidor.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "classflow.kafka.retry.max-attempts=1",
        "classflow.kafka.retry.initial-interval-ms=50",
        "classflow.kafka.retry.max-interval-ms=50"
})
@EmbeddedKafka(partitions = 3, topics = {
        "classflow.academic.grades", "classflow.academic.grades.DLT",
        "classflow.assistance.attendance", "classflow.assistance.attendance.DLT"
})
@DirtiesContext
@DisplayName("Consumidores Kafka (broker embebido)")
class AcademicEventsKafkaIntegrationTest {

    private static final String GRADES = "classflow.academic.grades";
    private static final String ATTENDANCE = "classflow.assistance.attendance";

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private NotificationRepository notificationRepository;

    private KafkaTemplate<String, String> rawTemplate;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        rawTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producerProps, new StringSerializer(), new StringSerializer()));
        // Espera a que cada contenedor tenga asignadas sus 3 particiones antes de publicar.
        registry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(container, 3));
    }

    @AfterEach
    void tearDown() {
        rawTemplate.destroy();
    }

    @Test
    @DisplayName("Una nota publicada crea la notificacion del estudiante")
    void gradeEventCreatesNotification() {
        rawTemplate.send(GRADES, "7", """
                {"eventId":"e-100","eventType":"GRADE_CREATED","gradeId":7,"studentId":321,
                 "evaluationId":3,"evaluationName":"Prueba 1","subjectId":2,"score":6.0,"maxScore":7.0,
                 "occurredAt":"2026-11-22T12:00:00Z"}""");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(321L))
                        .extracting(Notification::getSubject)
                        .containsExactly("Nueva calificacion: Prueba 1"));
    }

    @Test
    @DisplayName("Una ausencia publicada crea la notificacion del estudiante")
    void absenceEventCreatesNotification() {
        rawTemplate.send(ATTENDANCE, "654", """
                {"eventId":"e-200","eventType":"ATTENDANCE_REGISTERED","attendanceId":9,"studentId":654,
                 "courseId":4,"date":"2026-11-20","present":false,"justification":null,
                 "occurredAt":"2026-11-22T12:00:00Z"}""");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(654L)).hasSize(1));
    }

    @Test
    @DisplayName("JSON corrupto e payload invalido van al DLT y el consumidor sigue funcionando")
    void invalidMessagesGoToDeadLetterTopic() {
        try (Consumer<String, String> dltConsumer = dltConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dltConsumer, GRADES + ".DLT");

            rawTemplate.send(GRADES, "bad-json", "{esto no es json");
            rawTemplate.send(GRADES, "no-student", """
                    {"eventId":"e-300","eventType":"GRADE_CREATED","gradeId":8,
                     "score":5.0,"maxScore":7.0,"occurredAt":"2026-11-22T12:00:00Z"}""");

            var records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(20), 2);
            assertThat(records.count()).isEqualTo(2);
            for (ConsumerRecord<String, String> record : records) {
                assertThat(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(GRADES);
                assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNotBlank();
            }
        }

        // El consumidor no quedo bloqueado: un evento valido posterior se procesa.
        rawTemplate.send(GRADES, "9", """
                {"eventId":"e-301","eventType":"GRADE_UPDATED","gradeId":9,"studentId":987,
                 "evaluationId":3,"evaluationName":"Prueba 2","score":4.0,"maxScore":7.0,
                 "occurredAt":"2026-11-22T12:00:00Z"}""");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(987L)).hasSize(1));
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-test", "true", broker);
        props.put("auto.offset.reset", "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
