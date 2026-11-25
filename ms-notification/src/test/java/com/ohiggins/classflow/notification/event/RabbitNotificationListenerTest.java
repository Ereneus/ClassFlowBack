package com.ohiggins.classflow.notification.event;

import com.ohiggins.classflow.notification.dto.AlertRequestDTO;
import com.ohiggins.classflow.notification.enums.NotificationType;
import com.ohiggins.classflow.notification.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitNotificationListenerTest {

	private static final long TAG = 42L;

	@Mock
	private NotificationService notificationService;

	@Mock
	private Channel channel;

	@InjectMocks
	private RabbitNotificationListener listener;

	private final RabbitMessageEvent validEvent = new RabbitMessageEvent(17L, 10L, 20L, "Reunion", "Mañana 10:00");

	@Test
	void validEventCreatesNotificationAndAcks() throws Exception {
		listener.consumeMessageCreated(validEvent, channel, TAG, false);

		verify(notificationService).createNotification(argThat((AlertRequestDTO a) ->
				a.getUserId().equals(20L)
						&& a.getType() == NotificationType.ALERT
						&& a.getSubject().equals("Nuevo mensaje: Reunion")));
		verify(channel).basicAck(TAG, false);
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void eventWithoutReceiverIsSentToDlqWithoutRetry() throws Exception {
		RabbitMessageEvent invalid = new RabbitMessageEvent(18L, 10L, null, "Reunion", "Body");

		listener.consumeMessageCreated(invalid, channel, TAG, false);

		verifyNoInteractions(notificationService);
		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void eventWithoutSubjectIsSentToDlq() throws Exception {
		RabbitMessageEvent invalid = new RabbitMessageEvent(19L, 10L, 20L, " ", "Body");

		listener.consumeMessageCreated(invalid, channel, TAG, false);

		verifyNoInteractions(notificationService);
		verify(channel).basicNack(TAG, false, false);
	}

	@Test
	void processingErrorOnFirstDeliveryIsRequeued() throws Exception {
		when(notificationService.createNotification(any())).thenThrow(new IllegalStateException("db down"));

		listener.consumeMessageCreated(validEvent, channel, TAG, false);

		verify(channel).basicNack(TAG, false, true);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void processingErrorOnRedeliveryIsSentToDlq() throws Exception {
		when(notificationService.createNotification(any())).thenThrow(new IllegalStateException("db down"));

		listener.consumeMessageCreated(validEvent, channel, TAG, true);

		verify(channel).basicNack(TAG, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}
}
