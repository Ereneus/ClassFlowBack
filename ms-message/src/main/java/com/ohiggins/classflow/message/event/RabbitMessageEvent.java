package com.ohiggins.classflow.message.event;

public record RabbitMessageEvent(
        Long messageId,
        Long senderId,
        Long receiverId,
        String subject,
        String body
) {
}
