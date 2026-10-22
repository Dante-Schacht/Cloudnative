package com.cloudorders.notifications.processing;

import com.cloudorders.messaging.OrderCreatedEvent;

/** Lógica de negocio de un consumidor; lanza cualquier excepción para indicar fallo. */
@FunctionalInterface
public interface OrderEventHandler {

    void handle(OrderCreatedEvent event) throws Exception;
}
