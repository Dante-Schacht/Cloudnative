package com.cloudorders.notifications.consumers.audit;

import com.cloudorders.notifications.processing.ManualAckEventProcessor;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Dominio auditoría: consume orders.audit.queue (binding orders.#) y registra la traza del evento. */
@Component
public class OrderAuditListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditListener.class);

    private final ManualAckEventProcessor processor;

    public OrderAuditListener(ManualAckEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(id = "audit-listener", queues = "${cloudorders.rabbitmq.queues.audit.name}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.process("audit", message, channel, event ->
                log.info("AUDITORÍA {} eventId={} orderId={} status={} createdAt={}",
                        event.eventType(), event.eventId(), event.orderId(), event.status(), event.createdAt()));
    }
}
