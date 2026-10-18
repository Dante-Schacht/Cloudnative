package com.cloudorders.orders.repository;

import com.cloudorders.orders.model.Order;
import java.util.List;

/** Punto de extensión: se reemplazará por una implementación sobre PostgreSQL cloud. */
public interface OrderRepository {

    Order save(Order order);

    List<Order> findAll();

    /** Reserva el siguiente identificador único. */
    long nextId();
}
