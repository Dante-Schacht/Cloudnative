package com.cloudorders.notifications.consumers.notification;

import com.cloudorders.notifications.processing.ManualAckEventProcessor;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Dominio notificaciones: consume orders.created.queue y simula el aviso al cliente mediante logs. */
@Component
public class OrderNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);

    private final ManualAckEventProcessor processor;
    private final FailureSimulator failureSimulator;

    public OrderNotificationListener(ManualAckEventProcessor processor, FailureSimulator failureSimulator) {
        this.processor = processor;
        this.failureSimulator = failureSimulator;
    }

    @RabbitListener(id = "notification-listener", queues = "${cloudorders.rabbitmq.queues.created.name}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.process("notification", message, channel, event -> {
            failureSimulator.maybeFail(event);
            log.info("NOTIFICACIÓN SIMULADA -> {}: tu pedido #{} ({} x {}) fue recibido.",
                    event.customerName(), event.orderId(), event.quantity(), event.product());
        });
    }
}
