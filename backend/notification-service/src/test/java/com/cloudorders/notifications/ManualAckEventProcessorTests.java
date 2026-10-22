package com.cloudorders.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.cloudorders.messaging.OrderCreatedEvent;
import com.cloudorders.notifications.config.ProcessingProperties;
import com.cloudorders.notifications.processing.ManualAckEventProcessor;
import com.cloudorders.notifications.processing.NonRecoverableEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ManualAckEventProcessorTests {

    private static final long TAG = 7L;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Channel channel = mock(Channel.class);
    private ManualAckEventProcessor processor;

    @BeforeEach
    void setUp() {
        ProcessingProperties properties = new ProcessingProperties(3, Duration.ofMillis(1), 100, null);
        processor = new ManualAckEventProcessor(
                mapper, Validation.buildDefaultValidatorFactory().getValidator(), properties);
    }

    @Test
    void acksValidEventOnFirstAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", message(validEvent(UUID.randomUUID())), channel, event -> calls.incrementAndGet());

        assertThat(calls).hasValue(1);
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void nacksMalformedJsonWithoutRequeueAndWithoutCallingHandler() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", rawMessage("{no es json"), channel, event -> calls.incrementAndGet());

        assertThat(calls).hasValue(0);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void nacksEventThatFailsValidation() throws Exception {
        OrderCreatedEvent invalid = new OrderCreatedEvent(UUID.randomUUID(), OrderCreatedEvent.EVENT_TYPE, 1L,
                " ", "Notebook", 2, "", "PENDING", OffsetDateTime.now());
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", message(invalid), channel, event -> calls.incrementAndGet());

        assertThat(calls).hasValue(0);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void retriesTransientFailureAndAcksOnceItSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", message(validEvent(UUID.randomUUID())), channel, event -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("fallo transitorio");
            }
        });

        assertThat(calls).hasValue(3);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void sendsToDeadLetterAfterBoundedAttemptsWithoutRequeue() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", message(validEvent(UUID.randomUUID())), channel, event -> {
            calls.incrementAndGet();
            throw new IllegalStateException("siempre falla");
        });

        assertThat(calls).hasValue(3);
        verify(channel, times(1)).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void doesNotRetryNonRecoverableFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", message(validEvent(UUID.randomUUID())), channel, event -> {
            calls.incrementAndGet();
            throw new NonRecoverableEventException("datos imposibles de procesar");
        });

        assertThat(calls).hasValue(1);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void acksDuplicateEventWithoutReprocessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        processor.process("test", message(validEvent(eventId)), channel, event -> calls.incrementAndGet());
        processor.process("test", message(validEvent(eventId)), channel, event -> calls.incrementAndGet());

        assertThat(calls).hasValue(1);
        verify(channel, times(2)).basicAck(TAG, false);
    }

    private OrderCreatedEvent validEvent(UUID eventId) {
        return new OrderCreatedEvent(eventId, OrderCreatedEvent.EVENT_TYPE, 1L, "Juan Pérez", "Notebook", 2,
                "Entrega por la tarde", "PENDING", OffsetDateTime.now());
    }

    private Message message(OrderCreatedEvent event) throws Exception {
        return new Message(mapper.writeValueAsBytes(event), properties());
    }

    private Message rawMessage(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties());
    }

    private MessageProperties properties() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        return properties;
    }
}
