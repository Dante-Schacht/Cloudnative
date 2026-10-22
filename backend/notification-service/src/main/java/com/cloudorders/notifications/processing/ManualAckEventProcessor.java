package com.cloudorders.notifications.processing;

import com.cloudorders.messaging.OrderCreatedEvent;
import com.cloudorders.notifications.config.ProcessingProperties;
import com.rabbitmq.client.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

/**
 * Ciclo de consumo con confirmación manual (AcknowledgeMode.MANUAL), común a todos los listeners.
 *
 * <p>Decisiones de error, siempre sin requeue (no hay requeue infinito):
 * <ul>
 *   <li>Éxito del handler: {@code basicAck}.</li>
 *   <li>JSON ilegible o evento que no supera la validación: {@code basicNack(requeue=false)} -> DLQ, sin reintentos.</li>
 *   <li>{@link NonRecoverableEventException} del handler: {@code basicNack(requeue=false)} -> DLQ, sin reintentos.</li>
 *   <li>Cualquier otra excepción (transitoria): hasta {@code max-attempts} intentos con espera fija; si se
 *       agotan, {@code basicNack(requeue=false)} -> DLQ.</li>
 *   <li>Evento ya procesado (mismo eventId): {@code basicAck} sin reprocesar.</li>
 * </ul>
 * Todo NACK se registra en log con ERROR y el motivo; la DLQ la define la topología (DLX de cada cola).
 */
@Component
public class ManualAckEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(ManualAckEventProcessor.class);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ProcessingProperties properties;
    private final Map<String, ProcessedEventStore> storesByConsumer = new ConcurrentHashMap<>();

    public ManualAckEventProcessor(ObjectMapper objectMapper, Validator validator, ProcessingProperties properties) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.properties = properties;
    }

    public void process(String consumer, Message message, Channel channel, OrderEventHandler handler)
            throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();

        OrderCreatedEvent event;
        try {
            event = parseAndValidate(message);
        } catch (NonRecoverableEventException ex) {
            log.error("[{}] Mensaje inválido (deliveryTag={}, messageId={}): {}. NACK sin requeue -> DLQ",
                    consumer, tag, message.getMessageProperties().getMessageId(), ex.getMessage());
            channel.basicNack(tag, false, false);
            return;
        }

        ProcessedEventStore store = storesByConsumer.computeIfAbsent(
                consumer, name -> new ProcessedEventStore(properties.idempotencyCacheSize()));
        String eventKey = event.eventId().toString();
        if (store.contains(eventKey)) {
            log.warn("[{}] Evento duplicado {} (orderId={}); se confirma sin reprocesar",
                    consumer, eventKey, event.orderId());
            channel.basicAck(tag, false);
            return;
        }

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                handler.handle(event);
                store.markProcessed(eventKey);
                channel.basicAck(tag, false);
                log.info("[{}] ACK eventId={} orderId={} (intento {})", consumer, eventKey, event.orderId(), attempt);
                return;
            } catch (NonRecoverableEventException ex) {
                log.error("[{}] Fallo no recuperable eventId={} orderId={}: {}. NACK sin requeue -> DLQ",
                        consumer, eventKey, event.orderId(), ex.getMessage());
                channel.basicNack(tag, false, false);
                return;
            } catch (Exception ex) {
                log.warn("[{}] Fallo transitorio eventId={} orderId={} (intento {}/{}): {}",
                        consumer, eventKey, event.orderId(), attempt, properties.maxAttempts(), ex.getMessage());
                if (attempt < properties.maxAttempts() && !pauseBeforeRetry()) {
                    // Interrumpido (apagado): sin ACK/NACK, el broker reentrega el mensaje al reconectar.
                    return;
                }
            }
        }

        log.error("[{}] Reintentos agotados eventId={} orderId={}. NACK sin requeue -> DLQ",
                consumer, eventKey, event.orderId());
        channel.basicNack(tag, false, false);
    }

    private OrderCreatedEvent parseAndValidate(Message message) {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), OrderCreatedEvent.class);
        } catch (IOException ex) {
            throw new NonRecoverableEventException(
                    "JSON ilegible o con tipos incorrectos (" + ex.getClass().getSimpleName() + ")", ex);
        }
        if (event == null) {
            throw new NonRecoverableEventException("El cuerpo del mensaje está vacío");
        }
        Set<ConstraintViolation<OrderCreatedEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new NonRecoverableEventException("Evento inválido: " + violations.stream()
                    .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
        }
        return event;
    }

    private boolean pauseBeforeRetry() {
        try {
            Thread.sleep(properties.backoff().toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
