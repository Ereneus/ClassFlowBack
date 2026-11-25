package com.ohiggins.classflow.kafkaadmin.service;

import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ClusterDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ConsumerGroupDetailDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.CreateTopicRequest;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.OffsetResetStrategy;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.TopicDetailDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.TopicSummaryDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.UpdateTopicConfigRequest;
import com.ohiggins.classflow.kafkaadmin.exception.KafkaAdminOperationException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Ejercita el administrador contra un broker Kafka real (embebido).
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "classflow.academic.grades")
@DisplayName("KafkaAdminService (broker embebido)")
class KafkaAdminServiceIntegrationTest {

    @Autowired
    private KafkaAdminService adminService;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    @DisplayName("Describe el cluster")
    void describesCluster() {
        ClusterDTO cluster = adminService.describeCluster();

        assertThat(cluster.clusterId()).isNotBlank();
        assertThat(cluster.brokers()).hasSize(1);
        assertThat(cluster.controllerId()).isEqualTo(cluster.brokers().getFirst().id());
    }

    @Test
    @DisplayName("Ciclo de vida de un topico: crear, configurar y ampliar particiones")
    void topicLifecycle() {
        String name = "demo.lifecycle";
        TopicDetailDTO created = adminService.createTopic(new CreateTopicRequest(name, 2, (short) 1,
                Map.of("cleanup.policy", "compact", "retention.ms", "86400000")));

        assertThat(created.partitions()).hasSize(2);
        assertThat(created.configs()).containsKeys("cleanup.policy", "retention.ms");
        assertThat(created.configs().get("cleanup.policy").value()).isEqualTo("compact");
        assertThat(adminService.listTopics(false)).extracting(TopicSummaryDTO::name).contains(name);
        assertThatThrownBy(() -> adminService.createTopic(new CreateTopicRequest(name, 1, (short) 1, null)))
                .isInstanceOf(TopicExistsException.class);

        TopicDetailDTO updated = adminService.updateTopicConfig(name,
                new UpdateTopicConfigRequest(Map.of("retention.ms", "3600000"), List.of("cleanup.policy")));
        assertThat(updated.configs().get("retention.ms").value()).isEqualTo("3600000");
        assertThat(updated.configs()).doesNotContainKey("cleanup.policy");

        assertThat(adminService.increasePartitions(name, 4).partitions()).hasSize(4);
    }

    /**
     * En Windows el broker embebido no puede renombrar los archivos de un topico borrado
     * (AccessDeniedException) y se apaga, una limitacion conocida de Kafka en ese sistema.
     * En Linux (Docker, CI) el borrado funciona con normalidad.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("Elimina un topico no protegido")
    void deletesTopic() {
        String name = "demo.delete";
        adminService.createTopic(new CreateTopicRequest(name, 1, (short) 1, null));

        adminService.deleteTopic(name);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(adminService.listTopics(false)).extracting(TopicSummaryDTO::name).doesNotContain(name));
    }

    @Test
    @DisplayName("Errores del broker y reglas del servicio")
    void errors() {
        assertThatThrownBy(() -> adminService.describeTopic("no.existe", false))
                .isInstanceOf(UnknownTopicOrPartitionException.class);
        assertThatThrownBy(() -> adminService.updateTopicConfig("classflow.academic.grades",
                new UpdateTopicConfigRequest(Map.of("retention.ms", "no-es-numero"), null)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> adminService.deleteTopic("classflow.academic.grades"))
                .isInstanceOf(KafkaAdminOperationException.class)
                .hasMessageContaining("protegido");
        assertThatThrownBy(() -> adminService.deleteTopic("__consumer_offsets"))
                .isInstanceOf(KafkaAdminOperationException.class);
        assertThatThrownBy(() -> adminService.describeConsumerGroup("grupo-inexistente"))
                .isInstanceOf(GroupIdNotFoundException.class);
    }

    @Test
    @DisplayName("Inspecciona un consumer group con su lag y resetea sus offsets")
    void consumerGroupLagAndReset() {
        String topic = "demo.groups";
        adminService.createTopic(new CreateTopicRequest(topic, 1, (short) 1, null));

        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        for (int i = 0; i < 5; i++) {
            template.send(topic, "k" + i, "v" + i);
        }
        template.flush();
        template.destroy();

        // Un consumidor del grupo lee 5 mensajes, confirma y se desconecta (grupo queda EMPTY).
        Map<String, Object> props = KafkaTestUtils.consumerProps("demo-group", "false", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            int read = 0;
            long deadline = System.currentTimeMillis() + 15000;
            while (read < 5 && System.currentTimeMillis() < deadline) {
                read += consumer.poll(Duration.ofMillis(500)).count();
            }
            assertThat(read).isEqualTo(5);
            consumer.commitSync();
        }

        ConsumerGroupDetailDTO afterConsume = adminService.describeConsumerGroup("demo-group");
        assertThat(afterConsume.totalLag()).isZero();
        assertThat(afterConsume.offsets()).singleElement()
                .satisfies(o -> assertThat(o.committedOffset()).isEqualTo(5L));

        ConsumerGroupDetailDTO afterReset = adminService.resetOffsets("demo-group", topic, OffsetResetStrategy.EARLIEST);
        assertThat(afterReset.totalLag()).isEqualTo(5);
        assertThat(afterReset.state()).isEqualTo("EMPTY");

        adminService.deleteConsumerGroup("demo-group");
        assertThatThrownBy(() -> adminService.describeConsumerGroup("demo-group"))
                .isInstanceOf(GroupIdNotFoundException.class);
    }
}
