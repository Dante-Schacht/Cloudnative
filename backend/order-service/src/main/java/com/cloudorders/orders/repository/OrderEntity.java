package com.cloudorders.orders.repository;

import com.cloudorders.orders.model.Order;
import com.cloudorders.orders.model.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Fila de la tabla {@code orders}; el esquema lo crea Flyway (ver db/migration). */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private Long id;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 120)
    private String product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OrderEntity() {
    }

    public static OrderEntity from(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.id = order.id();
        entity.customerName = order.customerName();
        entity.product = order.product();
        entity.quantity = order.quantity();
        entity.notes = order.notes();
        entity.status = order.status();
        entity.createdAt = order.createdAt();
        return entity;
    }

    public Order toOrder() {
        return new Order(id, customerName, product, quantity, notes, status, createdAt);
    }
}
