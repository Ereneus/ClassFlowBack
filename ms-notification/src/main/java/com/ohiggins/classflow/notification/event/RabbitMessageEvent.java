package com.ohiggins.classflow.notification.event;

public record RabbitMessageEvent(
        Long messageId,
        Long senderId,
        Long receiverId,
        String subject,
        String body
) {
}
