package com.ohiggins.classflow.kafkaadmin.controller;

import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ClusterDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ConsumerGroupDetailDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ConsumerGroupSummaryDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.CreateTopicRequest;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.IncreasePartitionsRequest;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.ResetOffsetsRequest;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.TopicDetailDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.TopicSummaryDTO;
import com.ohiggins.classflow.kafkaadmin.dto.KafkaAdminDtos.UpdateTopicConfigRequest;
import com.ohiggins.classflow.kafkaadmin.service.KafkaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de administracion de Kafka (solo administradores).
 *
 * <pre>
 * GET    /api/kafka/cluster                               brokers y controller
 * GET    /api/kafka/topics?internal=false                 listar topicos
 * GET    /api/kafka/topics/{name}?includeDefaults=false   particiones, replicas, ISR y configuracion
 * POST   /api/kafka/topics                                crear topico con particiones, RF y configs
 * PATCH  /api/kafka/topics/{name}/configs                 modificar configuracion (set / reset)
 * POST   /api/kafka/topics/{name}/partitions              aumentar particiones
 * DELETE /api/kafka/topics/{name}                         eliminar topico (salvo protegidos)
 * GET    /api/kafka/consumer-groups                       listar consumer groups
 * GET    /api/kafka/consumer-groups/{groupId}             miembros, asignacion de particiones y lag
 * POST   /api/kafka/consumer-groups/{groupId}/reset-offsets  reposicionar offsets (grupo inactivo)
 * DELETE /api/kafka/consumer-groups/{groupId}             eliminar grupo (sin miembros)
 * </pre>
 */
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@Tag(name = "Administracion Kafka", description = "Topicos, configuraciones y consumer groups")
public class KafkaAdminController {

    private final KafkaAdminService adminService;

    @GetMapping("/cluster")
    @Operation(summary = "Describe el cluster: id, controller y brokers")
    public ClusterDTO cluster() {
        return adminService.describeCluster();
    }

    // ------------------------------------------------------------------ topicos

    @GetMapping("/topics")
    @Operation(summary = "Lista los topicos con su numero de particiones y factor de replicacion")
    public List<TopicSummaryDTO> listTopics(@RequestParam(defaultValue = "false") boolean internal) {
        return adminService.listTopics(internal);
    }

    @GetMapping("/topics/{name}")
    @Operation(summary = "Detalle de un topico: lider, replicas e ISR por particion y su configuracion")
    public TopicDetailDTO describeTopic(@PathVariable @Pattern(regexp = KafkaAdminDtos.TOPIC_NAME_REGEX) String name,
                                        @RequestParam(defaultValue = "false") boolean includeDefaults) {
        return adminService.describeTopic(name, includeDefaults);
    }

    @PostMapping("/topics")
    @Operation(summary = "Crea un topico con particiones, factor de replicacion y configuracion")
    public ResponseEntity<TopicDetailDTO> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTopic(request));
    }

    @PatchMapping("/topics/{name}/configs")
    @Operation(summary = "Modifica la configuracion del topico (retention.ms, cleanup.policy, ...)")
    public TopicDetailDTO updateConfigs(@PathVariable @Pattern(regexp = KafkaAdminDtos.TOPIC_NAME_REGEX) String name,
                                        @RequestBody UpdateTopicConfigRequest request) {
        return adminService.updateTopicConfig(name, request);
    }

    @PostMapping("/topics/{name}/partitions")
    @Operation(summary = "Aumenta el numero de particiones del topico")
    public TopicDetailDTO increasePartitions(@PathVariable @Pattern(regexp = KafkaAdminDtos.TOPIC_NAME_REGEX) String name,
                                             @Valid @RequestBody IncreasePartitionsRequest request) {
        return adminService.increasePartitions(name, request.totalCount());
    }

    @DeleteMapping("/topics/{name}")
    @Operation(summary = "Elimina un topico (los topicos internos y protegidos no se pueden eliminar)")
    public ResponseEntity<Void> deleteTopic(@PathVariable @Pattern(regexp = KafkaAdminDtos.TOPIC_NAME_REGEX) String name) {
        adminService.deleteTopic(name);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ consumer groups

    @GetMapping("/consumer-groups")
    @Operation(summary = "Lista los consumer groups y su estado")
    public List<ConsumerGroupSummaryDTO> listConsumerGroups() {
        return adminService.listConsumerGroups();
    }

    @GetMapping("/consumer-groups/{groupId}")
    @Operation(summary = "Miembros, particiones asignadas, offsets confirmados y lag del grupo")
    public ConsumerGroupDetailDTO describeConsumerGroup(@PathVariable String groupId) {
        return adminService.describeConsumerGroup(groupId);
    }

    @PostMapping("/consumer-groups/{groupId}/reset-offsets")
    @Operation(summary = "Mueve los offsets del grupo al inicio (reprocesar) o al final (saltar pendientes)")
    public ConsumerGroupDetailDTO resetOffsets(@PathVariable String groupId,
                                               @Valid @RequestBody ResetOffsetsRequest request) {
        return adminService.resetOffsets(groupId, request.topic(), request.strategy());
    }

    @DeleteMapping("/consumer-groups/{groupId}")
    @Operation(summary = "Elimina un consumer group sin miembros activos")
    public ResponseEntity<Void> deleteConsumerGroup(@PathVariable String groupId) {
        adminService.deleteConsumerGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
