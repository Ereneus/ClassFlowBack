package com.ohiggins.classflow.analytics.kafka;

import com.ohiggins.classflow.analytics.service.AcademicAnalyticsService;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Los eventos publicados en los dos topicos actualizan la proyeccion analitica, y un evento
 * invalido no detiene al consumidor.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "classflow.kafka.retry.max-attempts=1",
        "classflow.kafka.retry.initial-interval-ms=50",
        "classflow.kafka.retry.max-interval-ms=50"
})
@EmbeddedKafka(partitions = 3, topics = {
        "classflow.academic.grades", "classflow.academic.grades.DLT",
        "classflow.assistance.attendance", "classflow.assistance.attendance.DLT"
})
@DisplayName("Consumidores Kafka de analitica (broker embebido)")
class AnalyticsKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private AcademicAnalyticsService analyticsService;

    @Test
    @DisplayName("Notas y asistencia publicadas se reflejan en los indicadores")
    void eventsUpdateProjection() {
        registry.getListenerContainers().forEach(c -> ContainerTestUtils.waitForAssignment(c, 3));
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));

        template.send("classflow.academic.grades", "1", """
                {"eventId":"e-1","eventType":"GRADE_CREATED","gradeId":1,"studentId":50,"evaluationId":3,
                 "subjectId":9,"score":6.0,"maxScore":7.0,"occurredAt":"2026-11-22T12:00:00Z"}""");
        template.send("classflow.academic.grades", "x", "{json roto");
        template.send("classflow.assistance.attendance", "50", """
                {"eventId":"e-2","eventType":"ATTENDANCE_REGISTERED","attendanceId":4,"studentId":50,
                 "courseId":2,"date":"2026-11-20","present":false,"occurredAt":"2026-11-22T12:00:00Z"}""");
        template.send("classflow.academic.grades", "2", """
                {"eventId":"e-3","eventType":"GRADE_CREATED","gradeId":2,"studentId":50,"evaluationId":3,
                 "subjectId":9,"score":4.0,"maxScore":7.0,"occurredAt":"2026-11-22T12:01:00Z"}""");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(analyticsService.gradeStatsForStudent(50L)).hasValueSatisfying(s -> {
                assertThat(s.grades()).isEqualTo(2);
                assertThat(s.average()).isEqualTo(5.0);
            });
            assertThat(analyticsService.attendanceForCourse(2L))
                    .hasValueSatisfying(s -> assertThat(s.absent()).isEqualTo(1));
        });
        template.destroy();
    }
}
