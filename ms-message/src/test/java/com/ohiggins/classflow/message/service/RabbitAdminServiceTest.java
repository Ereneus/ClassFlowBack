package com.ohiggins.classflow.message.service;

import com.ohiggins.classflow.message.config.RabbitMqProperties;
import com.ohiggins.classflow.message.exception.ReservedRabbitResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

	private static final RabbitMqProperties PROPS = new RabbitMqProperties(
			"classflow.events", "classflow.messages.created", "message.created",
			"classflow.dlx", "classflow.messages.dlq");

	@Mock
	private AmqpAdmin amqpAdmin;

	private RabbitAdminService service;

	@BeforeEach
	void setUp() {
		service = new RabbitAdminService(amqpAdmin, PROPS);
	}

	@Test
	void createQueueDeclaresDurableQueue() {
		service.createQueue("reportes", true);

		verify(amqpAdmin).declareQueue(argThat((Queue q) -> q.getName().equals("reportes") && q.isDurable()));
	}

	@Test
	void createQueueDeclaresNonDurableQueue() {
		service.createQueue("temporal", false);

		verify(amqpAdmin).declareQueue(argThat((Queue q) -> q.getName().equals("temporal") && !q.isDurable()));
	}

	@Test
	void createExchangeUsesRequestedType() {
		service.createExchange("auditoria", ExchangeType.FANOUT, true);

		verify(amqpAdmin).declareExchange(argThat((Exchange e) ->
				e.getName().equals("auditoria") && e.getType().equals("fanout")));
	}

	@Test
	void reservedQueueCannotBeDeleted() {
		assertThatThrownBy(() -> service.deleteQueue("classflow.messages.created"))
				.isInstanceOf(ReservedRabbitResourceException.class);

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void reservedExchangeCannotBeDeleted() {
		assertThatThrownBy(() -> service.deleteExchange("classflow.events"))
				.isInstanceOf(ReservedRabbitResourceException.class);

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void ownQueueCanBeBoundToClassflowExchange() {
		service.createBinding("classflow.events", "auditoria", "message.*");

		verify(amqpAdmin).declareBinding(argThat((Binding b) ->
				b.getExchange().equals("classflow.events")
						&& b.getDestination().equals("auditoria")
						&& b.getRoutingKey().equals("message.*")));
	}

	@Test
	void bindingOfReservedQueueCannotBeRemoved() {
		assertThatThrownBy(() -> service.deleteBinding("classflow.events", "classflow.messages.created", "message.created"))
				.isInstanceOf(ReservedRabbitResourceException.class);

		verifyNoInteractions(amqpAdmin);
	}
}
