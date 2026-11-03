package com.ohiggins.classflow.notification.event;

import com.ohiggins.classflow.notification.config.RabbitMqConfig;
import com.ohiggins.classflow.notification.dto.AlertRequestDTO;
import com.ohiggins.classflow.notification.enums.NotificationType;
import com.ohiggins.classflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitNotificationListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMqConfig.MESSAGE_CREATED_QUEUE)
    public void consumeMessageCreated(RabbitMessageEvent event) {
        AlertRequestDTO alert = new AlertRequestDTO();
        alert.setUserId(event.receiverId());
        alert.setType(NotificationType.ALERT);
        alert.setSubject("Nuevo mensaje: " + event.subject());
        alert.setContent(event.body());
        notificationService.createNotification(alert);
        log.info("Created notification from RabbitMQ message event {}", event.messageId());
    }
}
