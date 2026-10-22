package com.cloudorders.orders.repository;

import com.cloudorders.orders.model.Order;
import java.util.List;

/** Punto de extensión: se reemplazará por una implementación sobre PostgreSQL cloud. */
public interface OrderRepository {

    Order save(Order order);

    List<Order> findAll();

    /** Elimina un pedido; se usa para revertir un guardado cuando el evento no pudo publicarse. */
    void deleteById(Long id);

    /** Reserva el siguiente identificador único. */
    long nextId();
}
