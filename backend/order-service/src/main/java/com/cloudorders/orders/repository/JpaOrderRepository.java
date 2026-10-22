package com.cloudorders.orders.repository;

import com.cloudorders.orders.model.Order;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Implementación de {@link OrderRepository} sobre PostgreSQL mediante Spring Data JPA. */
@Repository
public class JpaOrderRepository implements OrderRepository {

    private final OrderJpaRepository jpa;

    public JpaOrderRepository(OrderJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public long nextId() {
        return jpa.nextId();
    }

    @Override
    public Order save(Order order) {
        return jpa.saveAndFlush(OrderEntity.from(order)).toOrder();
    }

    @Override
    public List<Order> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(OrderEntity::toOrder).toList();
    }

    @Override
    public void deleteById(Long id) {
        if (jpa.existsById(id)) {
            jpa.deleteById(id);
        }
    }
}
