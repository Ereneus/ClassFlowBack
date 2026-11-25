package com.ohiggins.classflow.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

/**
 * DTOs de entrada y salida del administrador de Kafka.
 */
public final class KafkaAdminDtos {

    /** Nombre de topico valido en Kafka. */
    public static final String TOPIC_NAME_REGEX = "[a-zA-Z0-9._-]{1,249}";

    private KafkaAdminDtos() {
    }

    // ------------------------------------------------------------------ cluster

    public record BrokerDTO(int id, String host, int port, String rack) {
    }

    public record ClusterDTO(String clusterId, Integer controllerId, List<BrokerDTO> brokers) {
    }

    // ------------------------------------------------------------------ topicos

    public record TopicSummaryDTO(String name, boolean internal, int partitions, int replicationFactor) {
    }

    public record PartitionDTO(int partition, Integer leader, List<Integer> replicas, List<Integer> inSyncReplicas) {
    }

    /**
     * @param value   valor efectivo.
     * @param source  origen del valor (DYNAMIC_TOPIC_CONFIG = definido para el topico,
     *                DEFAULT_CONFIG / STATIC_BROKER_CONFIG = heredado).
     */
    public record ConfigEntryDTO(String value, String source, boolean readOnly) {
    }

    public record TopicDetailDTO(String name, boolean internal, int replicationFactor, List<PartitionDTO> partitions,
                                 Map<String, ConfigEntryDTO> configs) {
    }

    /**
     * Alta de un topico. Ejemplo de {@code configs}:
     * {@code {"cleanup.policy":"delete","retention.ms":"604800000","min.insync.replicas":"2"}}.
     */
    public record CreateTopicRequest(
            @NotBlank @Pattern(regexp = TOPIC_NAME_REGEX, message = "nombre de topico invalido") String name,
            @NotNull @Min(1) @Max(1000) Integer partitions,
            @NotNull @Min(1) @Max(32767) Short replicationFactor,
            Map<String, String> configs) {
    }

    /**
     * Cambio incremental de configuracion: {@code set} agrega o reemplaza valores y
     * {@code reset} elimina el valor propio del topico (vuelve al valor del broker).
     */
    public record UpdateTopicConfigRequest(Map<String, String> set, List<String> reset) {
    }

    /** Las particiones de un topico solo pueden aumentar. */
    public record IncreasePartitionsRequest(@NotNull @Min(1) @Max(1000) Integer totalCount) {
    }

    // ------------------------------------------------------------------ consumer groups

    public record ConsumerGroupSummaryDTO(String groupId, String state, boolean simple) {
    }

    public record MemberDTO(String memberId, String clientId, String host, List<String> assignedPartitions) {
    }

    /**
     * @param lag mensajes pendientes de consumir (offset final - offset confirmado).
     */
    public record PartitionLagDTO(String topic, int partition, Long committedOffset, Long endOffset, Long lag) {
    }

    public record ConsumerGroupDetailDTO(String groupId, String state, String partitionAssignor, Integer coordinator,
                                         List<MemberDTO> members, List<PartitionLagDTO> offsets, long totalLag) {
    }

    public enum OffsetResetStrategy { EARLIEST, LATEST }

    /**
     * Reposiciona los offsets de un grupo sobre un topico (el grupo debe estar sin consumidores
     * activos). EARLIEST permite reprocesar todo; LATEST salta lo pendiente.
     */
    public record ResetOffsetsRequest(
            @NotBlank @Pattern(regexp = TOPIC_NAME_REGEX, message = "nombre de topico invalido") String topic,
            @NotNull OffsetResetStrategy strategy) {
    }
}
