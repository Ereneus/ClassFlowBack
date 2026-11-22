package com.ohiggins.classflow.message.controller;

import com.ohiggins.classflow.message.dto.MessageDTO;
import com.ohiggins.classflow.message.security.AuthenticatedUser;
import com.ohiggins.classflow.message.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// addFilters = false: estos tests ejercitan solo el controller (service mockeado), no la
// cadena de Spring Security que ahora valida el JWT llamando a ms-auth.
@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application-test.properties")
class MessageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MessageService messageService;

	@Test
	void getAllReturnsMessages() throws Exception {
		when(messageService.findAll()).thenReturn(java.util.List.of(sampleMessage()));

		mockMvc.perform(get("/api/messages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].subject").value("Subject"));
	}

	@Test
	void sendCreatesMessage() throws Exception {
		when(messageService.send(any())).thenReturn(sampleMessage());

		mockMvc.perform(post("/api/messages/send")
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content("""
					{
					  "senderId": 10,
					  "receiverId": 20,
					  "subject": "Subject",
					  "body": "Body"
					}
					"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.receiverId").value(20));
	}

	@Test
	void markAsReadReturnsMessage() throws Exception {
		when(messageService.markAsRead(1L)).thenReturn(sampleMessage());

		mockMvc.perform(put("/api/messages/1/read"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(false));
	}

	@Test
	void deleteReturnsNoContent() throws Exception {
		doNothing().when(messageService).delete(1L);

		mockMvc.perform(delete("/api/messages/1"))
				.andExpect(status().isNoContent());
	}

	@Test
	void markAsReadOfAnotherUsersMessageIsForbidden() throws Exception {
		authenticateAs(99L, "STUDENT");
		when(messageService.findById(1L)).thenReturn(sampleMessage());

		mockMvc.perform(put("/api/messages/1/read"))
				.andExpect(status().isForbidden());

		verify(messageService, never()).markAsRead(any());
	}

	@Test
	void receiverCanMarkMessageAsRead() throws Exception {
		authenticateAs(20L, "GUARDIAN");
		when(messageService.findById(1L)).thenReturn(sampleMessage());
		when(messageService.markAsRead(1L)).thenReturn(sampleMessage());

		mockMvc.perform(put("/api/messages/1/read"))
				.andExpect(status().isOk());
	}

	@Test
	void sendUsesAuthenticatedUserAsSender() throws Exception {
		authenticateAs(20L, "STUDENT");
		when(messageService.send(any())).thenReturn(sampleMessage());

		mockMvc.perform(post("/api/messages/send")
				.contentType(MediaType.APPLICATION_JSON_VALUE)
				.content("""
					{
					  "senderId": 999,
					  "receiverId": 10,
					  "subject": "Subject",
					  "body": "Body"
					}
					"""))
				.andExpect(status().isCreated());

		verify(messageService).send(argThat(request -> request.getSenderId() == 20L));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// Sin filtros (addFilters = false) el contexto se fija a mano, como lo haria JwtAuthenticationFilter.
	private void authenticateAs(Long userId, String role) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(userId, "user@classflow.cl", role), null, List.of()));
	}

	private MessageDTO sampleMessage() {
		return MessageDTO.builder()
				.id(1L)
				.senderId(10L)
				.receiverId(20L)
				.subject("Subject")
				.body("Body")
				.read(false)
				.sentAt(LocalDateTime.of(2026, 5, 15, 10, 0))
				.build();
	}
}