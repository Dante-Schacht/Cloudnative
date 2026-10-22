package com.cloudorders.notifications.consumers.inventory;

import com.cloudorders.notifications.processing.ManualAckEventProcessor;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Dominio inventario: consume orders.inventory.queue y simula la reserva de stock mediante logs. */
@Component
public class OrderInventoryListener {

    private static final Logger log = LoggerFactory.getLogger(OrderInventoryListener.class);

    private final ManualAckEventProcessor processor;

    public OrderInventoryListener(ManualAckEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(id = "inventory-listener", queues = "${cloudorders.rabbitmq.queues.inventory.name}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.process("inventory", message, channel, event ->
                log.info("INVENTARIO SIMULADO: reserva de {} unidad(es) de '{}' para el pedido #{}",
                        event.quantity(), event.product(), event.orderId()));
    }
}
