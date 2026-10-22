package com.cloudorders.orders.messaging;

import com.cloudorders.orders.model.Order;

/** Puerto de salida de eventos de pedidos; aísla la lógica de negocio de RabbitMQ. */
public interface OrderEventPublisher {

    /**
     * Publica ORDER_CREATED y espera la confirmación del broker.
     *
     * @throws EventPublicationException si el broker no confirma o no puede enrutar el evento
     */
    void publishOrderCreated(Order order);
}
