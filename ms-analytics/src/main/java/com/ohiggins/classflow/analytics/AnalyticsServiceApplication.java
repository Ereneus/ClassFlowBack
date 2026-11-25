package com.ohiggins.classflow.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de analitica: consume en streaming los eventos de calificaciones y asistencia
 * (consumer group {@code classflow-analytics}) y expone indicadores agregados.
 */
@SpringBootApplication
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
