package com.ohiggins.classflow.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohiggins.classflow.notification.kafka.InvalidKafkaEventException;
import com.ohiggins.classflow.notification.kafka.event.AttendanceEvent;
import com.ohiggins.classflow.notification.kafka.event.GradeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuracion de los consumidores Kafka de ms-notification.
 *
 * <ul>
 *   <li><b>Conexion y consumer group</b>: {@code spring.kafka.*} en application.properties
 *       (bootstrap servers, group-id {@code classflow-notification}, offsets, aislamiento,
 *       asignacion cooperativa de particiones). Esas propiedades se reutilizan aqui.</li>
 *   <li><b>Deserializacion tipada</b>: una factory por topico, cada una con un
 *       {@link JsonDeserializer} hacia su DTO, envuelto en {@link ErrorHandlingDeserializer}
 *       para que un JSON corrupto no tumbe el consumidor (se envia al DLT).</li>
 *   <li><b>Errores</b>: {@link DefaultErrorHandler} con backoff exponencial; agotados los
 *       reintentos, o si el error no es reintentable (payload invalido, JSON corrupto), el
 *       mensaje se publica en {@code <topico>.DLT} con headers que explican la causa.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    // ---------------------------------------------------------------- container factories

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> gradeEventContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<?, ?> bootConsumerFactory,
            ObjectMapper objectMapper,
            DefaultErrorHandler kafkaErrorHandler) {
        return containerFactory(GradeEvent.class, configurer, bootConsumerFactory, objectMapper, kafkaErrorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> attendanceEventContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<?, ?> bootConsumerFactory,
            ObjectMapper objectMapper,
            DefaultErrorHandler kafkaErrorHandler) {
        return containerFactory(AttendanceEvent.class, configurer, bootConsumerFactory, objectMapper, kafkaErrorHandler);
    }

    /**
     * Construye una factory de contenedores para un DTO concreto. Toma las propiedades de
     * {@code spring.kafka.consumer.*} y {@code spring.kafka.listener.*} (concurrencia, ack-mode,
     * auto-startup) y solo cambia el deserializer del valor.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ConcurrentKafkaListenerContainerFactory<Object, Object> containerFactory(
            Class<T> type,
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<?, ?> bootConsumerFactory,
            ObjectMapper objectMapper,
            DefaultErrorHandler errorHandler) {

        Map<String, Object> props = new HashMap<>(bootConsumerFactory.getConfigurationProperties());
        DefaultKafkaConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(
                props,
                StringDeserializer::new,
                () -> new ErrorHandlingDeserializer<>(new JsonDeserializer<>(type, objectMapper, false)));

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, (ConsumerFactory) consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ---------------------------------------------------------------- manejo de errores

    /**
     * Reintentos con backoff exponencial (por defecto 1 s, 2 s, 4 s) dentro del mismo consumidor;
     * luego el mensaje va al DLT y el consumidor sigue con el siguiente offset, asi un mensaje
     * defectuoso nunca bloquea la particion.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer,
            @Value("${classflow.kafka.retry.max-attempts:3}") int maxRetries,
            @Value("${classflow.kafka.retry.initial-interval-ms:1000}") long initialInterval,
            @Value("${classflow.kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${classflow.kafka.retry.max-interval-ms:10000}") long maxInterval) {

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxInterval);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Kafka: {} enviado al DLT tras agotar reintentos o por error no reintentable: {}",
                    describe(record), rootMessage(ex));
            deadLetterPublishingRecoverer.accept(record, ex);
        }, backOff);

        // Un payload invalido no se arregla reintentando: va directo al DLT.
        // (DeserializationException y errores de conversion ya son no reintentables por defecto.)
        handler.addNotRetryableExceptions(InvalidKafkaEventException.class, IllegalArgumentException.class);

        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                log.warn("Kafka: fallo el intento {} de {}: {}", deliveryAttempt, describe(record), rootMessage(ex));
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.info("Kafka: {} recuperado (publicado en DLT)", describe(record));
            }

            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                log.error("Kafka: no se pudo publicar {} en el DLT; se reintentara el mensaje", describe(record), failure);
            }
        });
        return handler;
    }

    /**
     * Publica en {@code <topico-original>.DLT}, en la misma particion. Agrega headers
     * {@code kafka_dlt-*} con el topico, particion y offset originales y la excepcion.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> deadLetterKafkaTemplate,
            @Value("${classflow.kafka.dlt-suffix:.DLT}") String dltSuffix) {
        // Destino explicito: el sufijo por defecto de spring-kafka 3.x es "-dlt", y los topicos
        // DLT del cluster se crean como "<topico>.DLT" (auto.create.topics.enable=false).
        return new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + dltSuffix, record.partition()));
    }

    /**
     * Template exclusivo del DLT. El valor puede llegar como DTO (fallo al procesar) o como los
     * bytes originales (fallo al deserializar), por eso delega el serializer segun el tipo.
     * Reutiliza {@code spring.kafka.producer.*} (acks=all, idempotencia).
     */
    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate(ProducerFactory<?, ?> bootProducerFactory,
                                                                ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>(bootProducerFactory.getConfigurationProperties());

        JsonSerializer<Object> json = new JsonSerializer<>(objectMapper);
        json.setAddTypeInfo(false);
        // Orden importante (LinkedHashMap): byte[] tambien es asignable a Object.
        Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
        byType.put(byte[].class, new ByteArraySerializer());
        byType.put(Object.class, json);

        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(
                props, new StringSerializer(), new DelegatingByTypeSerializer(byType, true));
        return new KafkaTemplate<>(producerFactory);
    }

    // ---------------------------------------------------------------- utilidades de log

    static String describe(ConsumerRecord<?, ?> record) {
        return record.topic() + "-" + record.partition() + "@" + record.offset() + " (key=" + record.key() + ")";
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
