package com.cloudorders.orders.service;

import com.cloudorders.orders.dto.CreateOrderRequest;
import com.cloudorders.orders.model.Order;
import com.cloudorders.orders.model.OrderStatus;
import com.cloudorders.orders.repository.OrderRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final Clock clock;

    public OrderService(OrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Order create(CreateOrderRequest request) {
        String notes = request.notes() == null ? "" : request.notes().trim();
        Order order = new Order(
                repository.nextId(),
                request.customerName().trim(),
                request.product().trim(),
                request.quantity(),
                notes,
                OrderStatus.PENDING,
                OffsetDateTime.now(clock));
        return repository.save(order);
    }

    public List<Order> findAll() {
        return repository.findAll();
    }
}
