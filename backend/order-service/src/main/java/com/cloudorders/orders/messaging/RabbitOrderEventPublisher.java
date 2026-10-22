package com.cloudorders.orders.messaging;

import com.cloudorders.messaging.OrderCreatedEvent;
import com.cloudorders.orders.config.RabbitTopologyProperties;
import com.cloudorders.orders.model.Order;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitTopologyProperties properties;

    public RabbitOrderEventPublisher(RabbitTemplate rabbitTemplate, RabbitTopologyProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                OrderCreatedEvent.EVENT_TYPE,
                order.id(),
                order.customerName(),
                order.product(),
                order.quantity(),
                order.notes(),
                order.status().name(),
                order.createdAt());
        String eventId = event.eventId().toString();
        CorrelationData correlation = new CorrelationData(eventId);

        try {
            rabbitTemplate.convertAndSend(
                    properties.exchange(), properties.orderCreatedRoutingKey(), event,
                    message -> withMessageId(message, eventId), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new EventPublicationException("El broker rechazó (nack) el evento " + eventId
                        + ": " + confirm.getReason());
            }
            // Los retornos de mensajes no enrutables llegan antes de la confirmación.
            if (correlation.getReturned() != null) {
                throw new EventPublicationException("El evento " + eventId + " no pudo enrutarse a ninguna cola: "
                        + correlation.getReturned().getReplyText());
            }
        } catch (AmqpException e) {
            throw new EventPublicationException("No se pudo publicar el evento " + eventId + " en RabbitMQ.", e);
        } catch (TimeoutException e) {
            throw new EventPublicationException("El broker no confirmó el evento " + eventId + " a tiempo.", e);
        } catch (ExecutionException e) {
            throw new EventPublicationException("Falló la confirmación del evento " + eventId + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException("Publicación interrumpida del evento " + eventId + ".", e);
        }
        log.info("Evento {} publicado y confirmado: orderId={}, exchange={}, routingKey={}",
                eventId, order.id(), properties.exchange(), properties.orderCreatedRoutingKey());
    }

    private static Message withMessageId(Message message, String eventId) {
        message.getMessageProperties().setMessageId(eventId);
        return message;
    }
}
