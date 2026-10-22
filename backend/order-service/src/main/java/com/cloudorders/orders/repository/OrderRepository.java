package com.cloudorders.orders.repository;

import com.cloudorders.orders.model.Order;
import java.util.List;

/** Puerto de persistencia de pedidos; la implementación vigente usa PostgreSQL (ver JpaOrderRepository). */
public interface OrderRepository {

    Order save(Order order);

    List<Order> findAll();

    /** Elimina un pedido; se usa para revertir un guardado cuando el evento no pudo publicarse. */
    void deleteById(Long id);

    /** Reserva el siguiente identificador único. */
    long nextId();
}
