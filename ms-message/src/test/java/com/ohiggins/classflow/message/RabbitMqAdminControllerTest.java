package com.ohiggins.classflow.message.controller;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// addFilters = false: el acceso solo-administrador lo define SecurityConfig, aqui se prueba el controller.
@WebMvcTest(RabbitMqAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application-test.properties")
class RabbitMqAdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AmqpAdmin amqpAdmin;

	@Test
	void createQueueDeclaresDurableQueue() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/queues/reportes"))
				.andExpect(status().isCreated());

		verify(amqpAdmin).declareQueue(argThat((Queue q) -> q.getName().equals("reportes") && q.isDurable()));
	}

	@Test
	void deleteReservedQueueIsRejected() throws Exception {
		mockMvc.perform(delete("/api/rabbitmq/queues/classflow.messages.created"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void deleteReservedExchangeIsRejected() throws Exception {
		mockMvc.perform(delete("/api/rabbitmq/exchanges/classflow.events"))
				.andExpect(status().isConflict());

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void invalidNameIsRejected() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/queues/cola con espacios"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void unknownExchangeTypeIsRejected() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/exchanges/auditoria").param("type", "otro"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void createExchangeUsesRequestedType() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/exchanges/auditoria").param("type", "fanout"))
				.andExpect(status().isCreated());

		verify(amqpAdmin).declareExchange(argThat((Exchange e) -> e.getType().equals("fanout")));
	}

	@Test
	void createBindingAllowsOwnQueueOnClassflowExchange() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/bindings/classflow.events/auditoria")
						.param("routingKey", "message.*"))
				.andExpect(status().isCreated());

		verify(amqpAdmin).declareBinding(argThat((Binding b) ->
				b.getExchange().equals("classflow.events")
						&& b.getDestination().equals("auditoria")
						&& b.getRoutingKey().equals("message.*")));
	}

	@Test
	void deleteBindingOfReservedQueueIsRejected() throws Exception {
		mockMvc.perform(delete("/api/rabbitmq/bindings/classflow.events/classflow.messages.created")
						.param("routingKey", "message.created"))
				.andExpect(status().isConflict());

		verifyNoInteractions(amqpAdmin);
	}

	@Test
	void brokerUnavailableReturns503() throws Exception {
		doThrow(new AmqpConnectException(new ConnectException("refused")))
				.when(amqpAdmin).declareQueue(any());

		mockMvc.perform(post("/api/rabbitmq/queues/reportes"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("RabbitMQ"));
	}
}
