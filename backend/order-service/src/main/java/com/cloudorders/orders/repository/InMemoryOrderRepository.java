package com.cloudorders.orders.repository;

import com.cloudorders.orders.model.Order;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * Almacenamiento TEMPORAL en memoria: los datos se pierden al reiniciar el servicio.
 * Posteriormente será reemplazado por una base de datos cloud.
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    @Override
    public List<Order> findAll() {
        return orders.values().stream().sorted(Comparator.comparing(Order::id)).toList();
    }

    @Override
    public void deleteById(Long id) {
        orders.remove(id);
    }

    /** Vacía el almacenamiento; pensado para aislar las pruebas. */
    public void clear() {
        orders.clear();
    }
}
