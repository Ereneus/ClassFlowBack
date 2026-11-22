package com.ohiggins.classflow.kafkaadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservicio administrador de Kafka: gestiona topicos, sus configuraciones y particiones,
 * e inspecciona y mantiene los consumer groups del cluster.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KafkaAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaAdminServiceApplication.class, args);
    }
}
