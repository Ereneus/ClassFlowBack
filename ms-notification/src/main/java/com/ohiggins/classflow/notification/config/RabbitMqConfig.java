package com.ohiggins.classflow.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la misma topologia que ms-message (con los mismos argumentos) para que
 * el listener encuentre sus colas aunque ms-message aun no se haya conectado.
 * Los nombres vienen de {@link RabbitMqProperties}.
 */
@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange eventsExchange(RabbitMqProperties props) {
        return new TopicExchange(props.eventsExchange(), true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange(RabbitMqProperties props) {
        return new TopicExchange(props.deadLetterExchange(), true, false);
    }

    @Bean
    public Queue messageCreatedQueue(RabbitMqProperties props) {
        return QueueBuilder.durable(props.messageCreatedQueue())
                .withArgument("x-dead-letter-exchange", props.deadLetterExchange())
                .withArgument("x-dead-letter-routing-key", props.deadLetterQueue())
                .build();
    }

    @Bean
    public Queue deadLetterQueue(RabbitMqProperties props) {
        return QueueBuilder.durable(props.deadLetterQueue()).build();
    }

    @Bean
    public Binding messageCreatedBinding(TopicExchange eventsExchange, Queue messageCreatedQueue,
                                         RabbitMqProperties props) {
        return BindingBuilder.bind(messageCreatedQueue)
                .to(eventsExchange)
                .with(props.messageCreatedRoutingKey());
    }

    @Bean
    public Binding deadLetterBinding(TopicExchange deadLetterExchange, Queue deadLetterQueue,
                                     RabbitMqProperties props) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(props.deadLetterQueue());
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
