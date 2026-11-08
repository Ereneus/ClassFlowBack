package com.ohiggins.classflow.notification.event;

import com.ohiggins.classflow.notification.dto.AlertRequestDTO;
import com.ohiggins.classflow.notification.enums.NotificationType;
import com.ohiggins.classflow.notification.service.NotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio de mensajeria: convierte cada evento "mensaje creado" en una
 * notificacion para el destinatario.
 *
 * <p>Usa confirmacion manual (ackMode MANUAL) y decide de forma explicita el destino de
 * cada mensaje:
 * <ul>
 *   <li>Procesado correctamente: {@code basicAck}.</li>
 *   <li>Evento invalido (faltan datos): {@code basicNack} sin reencolar, va a la DLQ.</li>
 *   <li>Error al procesar por primera vez: {@code basicNack} reencolando, se reintenta una vez.</li>
 *   <li>Error en el reintento: {@code basicNack} sin reencolar, va a la DLQ.</li>
 * </ul>
 * Un mensaje que no se puede convertir a JSON lo rechaza el contenedor y tambien va a la DLQ.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitNotificationListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = "${classflow.rabbitmq.message-created-queue}", ackMode = "MANUAL")
    public void consumeMessageCreated(RabbitMessageEvent event,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                      @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered)
            throws IOException {
        try {
            validate(event);
            notificationService.createNotification(toAlert(event));
            channel.basicAck(deliveryTag, false);
            log.info("Created notification from RabbitMQ message event {} (ack)", event.messageId());
        } catch (InvalidMessageEventException e) {
            log.error("Invalid RabbitMQ message event, sent to DLQ: {} - {}", e.getMessage(), event);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            boolean requeue = !Boolean.TRUE.equals(redelivered);
            if (requeue) {
                log.warn("Error processing RabbitMQ message event {}, requeued for one retry", event.messageId(), e);
            } else {
                log.error("Error processing RabbitMQ message event {} after retry, sent to DLQ: {}",
                        event.messageId(), event, e);
            }
            channel.basicNack(deliveryTag, false, requeue);
        }
    }

    private static void validate(RabbitMessageEvent event) {
        if (event == null) {
            throw new InvalidMessageEventException("evento vacio");
        }
        if (event.messageId() == null || event.receiverId() == null) {
            throw new InvalidMessageEventException("faltan messageId o receiverId");
        }
        if (event.subject() == null || event.subject().isBlank()) {
            throw new InvalidMessageEventException("falta el asunto del mensaje");
        }
    }

    private static AlertRequestDTO toAlert(RabbitMessageEvent event) {
        AlertRequestDTO alert = new AlertRequestDTO();
        alert.setUserId(event.receiverId());
        alert.setType(NotificationType.ALERT);
        alert.setSubject("Nuevo mensaje: " + event.subject());
        alert.setContent(event.body());
        return alert;
    }
}
