package com.ohiggins.classflow.kafkaadmin.service;

import com.ohiggins.classflow.kafkaadmin.config.KafkaAdminProperties;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.BrokerDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ClusterDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ConfigEntryDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ConsumerGroupDetailDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ConsumerGroupSummaryDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.CreateTopicRequest;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.MemberDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.OffsetResetStrategy;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.PartitionDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.PartitionLagDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.TopicDetailDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.TopicSummaryDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.UpdateTopicConfigRequest;
import com.ohiggins.classflow.kafkaadmin.exception.KafkaAdminOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Operaciones de administracion sobre el cluster Kafka a traves de {@link AdminClient}.
 *
 * <p>Todas las llamadas esperan el resultado con el tiempo maximo de
 * {@code classflow.kafka.admin.operation-timeout}. Los errores del broker (topico inexistente,
 * configuracion invalida, grupo con miembros...) se propagan como las excepciones de Kafka y
 * los traduce a HTTP el {@code GlobalExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminService {

    private final AdminClient adminClient;
    private final KafkaAdminProperties properties;

    // ------------------------------------------------------------------ cluster

    public ClusterDTO describeCluster() {
        var result = adminClient.describeCluster();
        Node controller = await(result.controller());
        List<BrokerDTO> brokers = await(result.nodes()).stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> new BrokerDTO(n.id(), n.host(), n.port(), n.rack()))
                .toList();
        return new ClusterDTO(await(result.clusterId()), controller != null ? controller.id() : null, brokers);
    }

    // ------------------------------------------------------------------ topicos

    public List<TopicSummaryDTO> listTopics(boolean includeInternal) {
        Collection<TopicListing> listings = await(adminClient
                .listTopics(new ListTopicsOptions().listInternal(includeInternal)).listings());
        if (listings.isEmpty()) {
            return List.of();
        }
        Map<String, TopicDescription> descriptions = await(adminClient
                .describeTopics(listings.stream().map(TopicListing::name).toList()).allTopicNames());
        return descriptions.values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(d -> new TopicSummaryDTO(d.name(), d.isInternal(), d.partitions().size(), replicationFactor(d)))
                .toList();
    }

    public TopicDetailDTO describeTopic(String name, boolean includeDefaults) {
        TopicDescription description = await(adminClient.describeTopics(List.of(name)).allTopicNames()).get(name);
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
        Config config = await(adminClient.describeConfigs(List.of(resource)).all()).get(resource);

        Map<String, ConfigEntryDTO> configs = new TreeMap<>();
        for (ConfigEntry entry : config.entries()) {
            boolean topicLevel = entry.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG;
            if (includeDefaults || topicLevel) {
                configs.put(entry.name(), new ConfigEntryDTO(entry.isSensitive() ? "******" : entry.value(),
                        entry.source().name(), entry.isReadOnly()));
            }
        }

        List<PartitionDTO> partitions = description.partitions().stream()
                .map(KafkaAdminService::toPartitionDTO)
                .toList();
        return new TopicDetailDTO(description.name(), description.isInternal(), replicationFactor(description),
                partitions, configs);
    }

    public TopicDetailDTO createTopic(CreateTopicRequest request) {
        NewTopic topic = new NewTopic(request.name(), request.partitions(), request.replicationFactor());
        if (request.configs() != null && !request.configs().isEmpty()) {
            topic.configs(request.configs());
        }
        await(adminClient.createTopics(List.of(topic)).all());
        log.info("Kafka admin: topico {} creado ({} particiones, RF {}, configs {})", request.name(),
                request.partitions(), request.replicationFactor(), request.configs());
        return describeTopic(request.name(), false);
    }

    public TopicDetailDTO updateTopicConfig(String name, UpdateTopicConfigRequest request) {
        requireNotInternal(name);
        List<AlterConfigOp> ops = new ArrayList<>();
        Optional.ofNullable(request.set()).orElse(Map.of()).forEach((key, value) ->
                ops.add(new AlterConfigOp(new ConfigEntry(key, value), AlterConfigOp.OpType.SET)));
        Optional.ofNullable(request.reset()).orElse(List.of()).forEach(key ->
                ops.add(new AlterConfigOp(new ConfigEntry(key, null), AlterConfigOp.OpType.DELETE)));
        if (ops.isEmpty()) {
            throw new KafkaAdminOperationException(HttpStatus.BAD_REQUEST, "No se indico ninguna configuracion a cambiar");
        }

        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
        await(adminClient.incrementalAlterConfigs(Map.of(resource, ops)).all());
        log.info("Kafka admin: configuracion de {} actualizada (set {}, reset {})", name, request.set(), request.reset());
        return describeTopic(name, false);
    }

    public TopicDetailDTO increasePartitions(String name, int totalCount) {
        requireNotInternal(name);
        await(adminClient.createPartitions(Map.of(name, NewPartitions.increaseTo(totalCount))).all());
        log.info("Kafka admin: {} ahora tiene {} particiones", name, totalCount);
        return describeTopic(name, false);
    }

    public void deleteTopic(String name) {
        if (properties.isProtected(name)) {
            throw new KafkaAdminOperationException(HttpStatus.CONFLICT,
                    "El topico " + name + " es interno o protegido y no se puede eliminar");
        }
        await(adminClient.deleteTopics(List.of(name)).all());
        log.warn("Kafka admin: topico {} eliminado", name);
    }

    // ------------------------------------------------------------------ consumer groups

    public List<ConsumerGroupSummaryDTO> listConsumerGroups() {
        return await(adminClient.listConsumerGroups().all()).stream()
                .map(g -> new ConsumerGroupSummaryDTO(g.groupId(),
                        g.state().map(Enum::name).orElse("UNKNOWN"), g.isSimpleConsumerGroup()))
                .sorted(Comparator.comparing(ConsumerGroupSummaryDTO::groupId))
                .toList();
    }

    public ConsumerGroupDetailDTO describeConsumerGroup(String groupId) {
        ConsumerGroupDescription group = await(adminClient.describeConsumerGroups(List.of(groupId)).all()).get(groupId);
        Map<TopicPartition, OffsetAndMetadata> committed = await(adminClient
                .listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());

        if (group.state() == ConsumerGroupState.DEAD && committed.isEmpty()) {
            throw new GroupIdNotFoundException("El consumer group " + groupId + " no existe");
        }

        Map<TopicPartition, ListOffsetsResultInfo> endOffsets = committed.isEmpty() ? Map.of()
                : await(adminClient.listOffsets(committed.keySet().stream()
                        .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()))).all());

        List<PartitionLagDTO> offsets = committed.entrySet().stream()
                .map(e -> {
                    TopicPartition tp = e.getKey();
                    Long committedOffset = e.getValue() != null ? e.getValue().offset() : null;
                    Long end = Optional.ofNullable(endOffsets.get(tp)).map(ListOffsetsResultInfo::offset).orElse(null);
                    Long lag = committedOffset != null && end != null ? Math.max(0, end - committedOffset) : null;
                    return new PartitionLagDTO(tp.topic(), tp.partition(), committedOffset, end, lag);
                })
                .sorted(Comparator.comparing(PartitionLagDTO::topic).thenComparingInt(PartitionLagDTO::partition))
                .toList();
        long totalLag = offsets.stream().map(PartitionLagDTO::lag).filter(Objects::nonNull).mapToLong(Long::longValue).sum();

        List<MemberDTO> members = group.members().stream()
                .map(m -> new MemberDTO(m.consumerId(), m.clientId(), m.host(),
                        m.assignment().topicPartitions().stream()
                                .map(tp -> tp.topic() + "-" + tp.partition())
                                .sorted()
                                .toList()))
                .sorted(Comparator.comparing(MemberDTO::clientId))
                .toList();

        return new ConsumerGroupDetailDTO(group.groupId(), group.state().name(), group.partitionAssignor(),
                group.coordinator() != null ? group.coordinator().id() : null, members, offsets, totalLag);
    }

    /**
     * Mueve los offsets confirmados del grupo al inicio o al final del topico. Kafka solo lo
     * permite si el grupo no tiene consumidores conectados.
     */
    public ConsumerGroupDetailDTO resetOffsets(String groupId, String topic, OffsetResetStrategy strategy) {
        ConsumerGroupDescription group = await(adminClient.describeConsumerGroups(List.of(groupId)).all()).get(groupId);
        if (group.state() != ConsumerGroupState.EMPTY && group.state() != ConsumerGroupState.DEAD) {
            throw new KafkaAdminOperationException(HttpStatus.CONFLICT, "El consumer group " + groupId
                    + " tiene consumidores activos (estado " + group.state() + "); detenlos antes de resetear offsets");
        }

        TopicDescription description = await(adminClient.describeTopics(List.of(topic)).allTopicNames()).get(topic);
        OffsetSpec spec = strategy == OffsetResetStrategy.EARLIEST ? OffsetSpec.earliest() : OffsetSpec.latest();
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        description.partitions().forEach(p -> request.put(new TopicPartition(topic, p.partition()), spec));

        Map<TopicPartition, OffsetAndMetadata> target = await(adminClient.listOffsets(request).all()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue().offset())));
        await(adminClient.alterConsumerGroupOffsets(groupId, target).all());
        log.warn("Kafka admin: offsets del grupo {} en {} movidos a {}", groupId, topic, strategy);
        return describeConsumerGroup(groupId);
    }

    public void deleteConsumerGroup(String groupId) {
        await(adminClient.deleteConsumerGroups(List.of(groupId)).all());
        log.warn("Kafka admin: consumer group {} eliminado", groupId);
    }

    // ------------------------------------------------------------------ utilidades

    private void requireNotInternal(String topic) {
        if (topic.startsWith("__")) {
            throw new KafkaAdminOperationException(HttpStatus.CONFLICT, "El topico " + topic + " es interno de Kafka");
        }
    }

    private static int replicationFactor(TopicDescription description) {
        return description.partitions().isEmpty() ? 0 : description.partitions().getFirst().replicas().size();
    }

    private static PartitionDTO toPartitionDTO(TopicPartitionInfo p) {
        return new PartitionDTO(p.partition(),
                p.leader() != null ? p.leader().id() : null,
                p.replicas().stream().map(Node::id).toList(),
                p.isr().stream().map(Node::id).toList());
    }

    /**
     * Espera el resultado de una operacion del AdminClient. Desenvuelve la causa real
     * (las excepciones de Kafka son RuntimeException) para que el handler HTTP la traduzca.
     */
    private <T> T await(KafkaFuture<T> future) {
        try {
            return future.get(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new org.apache.kafka.common.errors.TimeoutException(
                    "El cluster Kafka no respondio en " + properties.operationTimeout().toSeconds() + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operacion interrumpida", e);
        }
    }
}
