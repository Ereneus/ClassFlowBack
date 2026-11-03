package com.ohiggins.classflow.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Declara la misma topologia que ms-message (con los mismos argumentos) para que
 * el listener encuentre sus colas aunque ms-message aun no se haya conectado.
 */
@Configuration
@Slf4j
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE = "classflow.events";
    public static final String MESSAGE_CREATED_QUEUE = "classflow.messages.created";
    public static final String MESSAGE_CREATED_ROUTING_KEY = "message.created";
    public static final String DEAD_LETTER_EXCHANGE = "classflow.dlx";
    public static final String DEAD_LETTER_QUEUE = "classflow.messages.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue messageCreatedQueue() {
        return QueueBuilder.durable(MESSAGE_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding messageCreatedBinding(TopicExchange eventsExchange, Queue messageCreatedQueue) {
        return BindingBuilder.bind(messageCreatedQueue)
                .to(eventsExchange)
                .with(MESSAGE_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(TopicExchange deadLetterExchange, Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_QUEUE);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Al agotar los reintentos registra el error y el contenido, y rechaza el mensaje
     * sin reencolarlo para que quede guardado en la DLQ.
     */
    @Bean
    public MessageRecoverer messageRecoverer() {
        return new RejectAndDontRequeueRecoverer() {
            @Override
            public void recover(Message message, Throwable cause) {
                log.error(
                        "RabbitMQ message moved to DLQ after retries. routingKey={}, body={}",
                        message.getMessageProperties().getReceivedRoutingKey(),
                        new String(message.getBody(), StandardCharsets.UTF_8),
                        cause
                );
                super.recover(message, cause);
            }
        };
    }
}
