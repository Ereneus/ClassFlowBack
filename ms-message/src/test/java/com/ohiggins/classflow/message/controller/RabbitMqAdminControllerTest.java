package com.ohiggins.classflow.message.controller;

import com.ohiggins.classflow.message.exception.ReservedRabbitResourceException;
import com.ohiggins.classflow.message.service.ExchangeType;
import com.ohiggins.classflow.message.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
	private RabbitAdminService rabbitAdminService;

	@Test
	void createQueueDelegatesToServiceAsDurable() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/queues/reportes"))
				.andExpect(status().isCreated());

		verify(rabbitAdminService).createQueue("reportes", true);
	}

	@Test
	void createExchangeParsesTypeIgnoringCase() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/exchanges/auditoria").param("type", "FANOUT"))
				.andExpect(status().isCreated());

		verify(rabbitAdminService).createExchange("auditoria", ExchangeType.FANOUT, true);
	}

	@Test
	void createBindingDelegatesRoutingKey() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/bindings/classflow.events/auditoria")
						.param("routingKey", "message.*"))
				.andExpect(status().isCreated());

		verify(rabbitAdminService).createBinding("classflow.events", "auditoria", "message.*");
	}

	@Test
	void deleteQueueReturnsNoContent() throws Exception {
		mockMvc.perform(delete("/api/rabbitmq/queues/reportes"))
				.andExpect(status().isNoContent());

		verify(rabbitAdminService).deleteQueue("reportes");
	}

	@Test
	void invalidQueueNameIsRejectedBeforeReachingService() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/queues/cola con espacios"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));

		verifyNoInteractions(rabbitAdminService);
	}

	@Test
	void tooLongQueueNameIsRejected() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/queues/" + "a".repeat(256)))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(rabbitAdminService);
	}

	@Test
	void unknownExchangeTypeIsRejected() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/exchanges/auditoria").param("type", "otro"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("type")));

		verifyNoInteractions(rabbitAdminService);
	}

	@Test
	void invalidRoutingKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/rabbitmq/bindings/classflow.events/auditoria")
						.param("routingKey", "clave con espacios"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(rabbitAdminService);
	}

	@Test
	void reservedResourceReturns409() throws Exception {
		doThrow(new ReservedRabbitResourceException("classflow.messages.created"))
				.when(rabbitAdminService).deleteQueue("classflow.messages.created");

		mockMvc.perform(delete("/api/rabbitmq/queues/classflow.messages.created"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void brokerUnavailableReturns503() throws Exception {
		doThrow(new AmqpConnectException(new ConnectException("refused")))
				.when(rabbitAdminService).createQueue(anyString(), anyBoolean());

		mockMvc.perform(post("/api/rabbitmq/queues/reportes"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("RabbitMQ"));
	}
}
