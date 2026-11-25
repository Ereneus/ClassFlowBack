package com.ohiggins.classflow.kafkaadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Parametros del administrador.
 *
 * @param operationTimeout tiempo maximo de espera de cada operacion contra el cluster.
 * @param protectedTopics  topicos de los que depende el sistema: no se pueden eliminar desde la API.
 */
@Validated
@ConfigurationProperties(prefix = "classflow.kafka.admin")
public record KafkaAdminProperties(Duration operationTimeout, List<String> protectedTopics) {

    public KafkaAdminProperties {
        operationTimeout = operationTimeout != null ? operationTimeout : Duration.ofSeconds(15);
        protectedTopics = protectedTopics != null ? List.copyOf(protectedTopics) : List.of();
    }

    public boolean isProtected(String topic) {
        return topic.startsWith("__") || protectedTopics.contains(topic);
    }
}
