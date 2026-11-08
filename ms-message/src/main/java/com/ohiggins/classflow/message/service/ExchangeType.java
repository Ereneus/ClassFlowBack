package com.ohiggins.classflow.message.service;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.TopicExchange;

import java.util.Locale;

/**
 * Tipos de exchange que la API de administracion permite crear.
 */
public enum ExchangeType {
    TOPIC, DIRECT, FANOUT, HEADERS;

    /** Expresion usada para validar el parametro {@code type} de la API (sin distinguir mayusculas). */
    public static final String PATTERN = "(?i)topic|direct|fanout|headers";

    /**
     * Convierte el valor recibido por la API, sin distinguir mayusculas.
     *
     * @param value nombre del tipo (topic, direct, fanout o headers).
     * @return tipo de exchange correspondiente.
     */
    public static ExchangeType from(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Construye el exchange de este tipo.
     *
     * @param name    nombre del exchange.
     * @param durable si sobrevive a un reinicio del broker.
     * @return exchange listo para declararse.
     */
    public Exchange build(String name, boolean durable) {
        return switch (this) {
            case TOPIC -> new TopicExchange(name, durable, false);
            case DIRECT -> new DirectExchange(name, durable, false);
            case FANOUT -> new FanoutExchange(name, durable, false);
            case HEADERS -> new HeadersExchange(name, durable, false);
        };
    }
}
