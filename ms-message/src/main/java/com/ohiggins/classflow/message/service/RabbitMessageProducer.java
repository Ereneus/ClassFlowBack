package com.ohiggins.classflow.message.service;

import com.ohiggins.classflow.message.config.RabbitMqProperties;
import com.ohiggins.classflow.message.event.RabbitMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties props;

    /**
     * Publica el evento de mensaje creado. Es best-effort: si el broker no esta
     * disponible se registra el error y el mensaje ya guardado no se ve afectado.
     */
    public void publishMessageCreated(RabbitMessageEvent event) {
        try {
            rabbitTemplate.convertAndSend(props.eventsExchange(), props.messageCreatedRoutingKey(), event);
            log.info("Published RabbitMQ message-created event for message {}", event.messageId());
        } catch (AmqpException e) {
            log.error("Could not publish RabbitMQ message-created event for message {}", event.messageId(), e);
        }
    }
}
