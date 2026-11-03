package com.ohiggins.classflow.message.service;

import com.ohiggins.classflow.message.config.RabbitMqConfig;
import com.ohiggins.classflow.message.event.RabbitMessageEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

	@Mock
	private RabbitTemplate rabbitTemplate;

	@InjectMocks
	private RabbitMessageProducer producer;

	private final RabbitMessageEvent event = new RabbitMessageEvent(1L, 10L, 20L, "Subject", "Body");

	@Test
	void publishMessageCreatedSendsToEventsExchange() {
		producer.publishMessageCreated(event);

		verify(rabbitTemplate).convertAndSend(
				RabbitMqConfig.EVENTS_EXCHANGE, RabbitMqConfig.MESSAGE_CREATED_ROUTING_KEY, event);
	}

	@Test
	void publishMessageCreatedDoesNotPropagateBrokerErrors() {
		doThrow(new AmqpConnectException(new ConnectException("refused")))
				.when(rabbitTemplate).convertAndSend(
						RabbitMqConfig.EVENTS_EXCHANGE, RabbitMqConfig.MESSAGE_CREATED_ROUTING_KEY, event);

		assertThatCode(() -> producer.publishMessageCreated(event)).doesNotThrowAnyException();
	}
}
