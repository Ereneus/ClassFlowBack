package com.ohiggins.classflow.kafkaadmin.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Crea un {@link AdminClient} con la misma configuracion que el {@link KafkaAdmin} de Spring
 * Boot ({@code spring.kafka.bootstrap-servers} y {@code spring.kafka.admin.*}). Es thread-safe
 * y se reutiliza en todas las peticiones; se cierra al detener el servicio.
 */
@Configuration
public class KafkaAdminClientConfig {

    @Bean(destroyMethod = "close")
    public AdminClient kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }
}
