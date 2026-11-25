package com.ohiggins.classflow.assistance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Configuracion del productor Kafka.
 *
 * <p>Las propiedades del cliente (bootstrap servers, acks=all, idempotencia, compresion,
 * timeouts, serializer de la key) estan en {@code application.properties} bajo
 * {@code spring.kafka}. Aqui solo se reemplaza el serializer del valor por un
 * {@link JsonSerializer} construido con el {@link ObjectMapper} de Spring, para que las fechas
 * viajen en ISO-8601 y sin headers de tipo Java: cada consumidor deserializa a su propio DTO.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public DefaultKafkaProducerFactoryCustomizer jsonValueSerializerCustomizer(ObjectMapper objectMapper) {
        return producerFactory -> ((DefaultKafkaProducerFactory<Object, Object>) producerFactory)
                .setValueSerializerSupplier(() -> {
                    JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
                    serializer.setAddTypeInfo(false);
                    return serializer;
                });
    }
}
