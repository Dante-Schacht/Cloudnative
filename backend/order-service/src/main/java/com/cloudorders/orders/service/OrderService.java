package com.cloudorders.orders.service;

import com.cloudorders.orders.dto.CreateOrderRequest;
import com.cloudorders.orders.messaging.EventPublicationException;
import com.cloudorders.orders.messaging.OrderEventPublisher;
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
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository repository, Clock clock, OrderEventPublisher eventPublisher) {
        this.repository = repository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Guarda el pedido y publica ORDER_CREATED. Si el broker no confirma el evento, se revierte el
     * guardado y se propaga EventPublicationException: no hay outbox, por lo que no se acepta un
     * pedido cuyo evento no se pudo confirmar.
     */
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
        Order saved = repository.save(order);
        try {
            eventPublisher.publishOrderCreated(saved);
        } catch (EventPublicationException ex) {
            repository.deleteById(saved.id());
            throw ex;
        }
        return saved;
    }

    public List<Order> findAll() {
        return repository.findAll();
    }
}
