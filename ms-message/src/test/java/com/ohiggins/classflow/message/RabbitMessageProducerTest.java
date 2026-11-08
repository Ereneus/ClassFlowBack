package com.ohiggins.classflow.message.service;

import com.ohiggins.classflow.message.config.RabbitMqProperties;
import com.ohiggins.classflow.message.event.RabbitMessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMessageProducerTest {

	private static final RabbitMqProperties PROPS = new RabbitMqProperties(
			"classflow.events", "classflow.messages.created", "message.created",
			"classflow.dlx", "classflow.messages.dlq");

	@Mock
	private RabbitTemplate rabbitTemplate;

	private RabbitMessageProducer producer;

	private final RabbitMessageEvent event = new RabbitMessageEvent(1L, 10L, 20L, "Subject", "Body");

	@BeforeEach
	void setUp() {
		producer = new RabbitMessageProducer(rabbitTemplate, PROPS);
	}

	@Test
	void publishMessageCreatedSendsToEventsExchange() {
		producer.publishMessageCreated(event);

		verify(rabbitTemplate).convertAndSend("classflow.events", "message.created", event);
	}

	@Test
	void publishMessageCreatedDoesNotPropagateBrokerErrors() {
		doThrow(new AmqpConnectException(new ConnectException("refused")))
				.when(rabbitTemplate).convertAndSend("classflow.events", "message.created", event);

		assertThatCode(() -> producer.publishMessageCreated(event)).doesNotThrowAnyException();
	}
}
