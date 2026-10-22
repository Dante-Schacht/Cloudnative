package com.cloudorders.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudorders.orders.model.Order;
import com.cloudorders.orders.model.OrderStatus;
import com.cloudorders.orders.repository.OrderRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integración contra un PostgreSQL real (Flyway + JPA). Se omite si Docker no está disponible. */
@SpringBootTest(properties = {
        "spring.rabbitmq.username=test",
        "spring.rabbitmq.password=test",
        "spring.datasource.username=unused",
        "spring.datasource.password=unused",
        "cloudorders.rabbitmq.declare-on-startup=false"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private OrderRepository repository;

    @Test
    void persistsAndListsOrdersInIdOrderWithPostgresTypes() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC);
        long first = repository.nextId();
        long second = repository.nextId();
        assertThat(second).isEqualTo(first + 1);

        repository.save(new Order(second, "Beta", "Mouse", 3, "", OrderStatus.PENDING, createdAt));
        repository.save(new Order(first, "Alfa", "Notebook", 1, "frágil", OrderStatus.PENDING, createdAt));

        List<Order> orders = repository.findAll();
        assertThat(orders).extracting(Order::id).containsSubsequence(first, second);
        Order stored = orders.stream().filter(o -> o.id() == first).findFirst().orElseThrow();
        assertThat(stored.customerName()).isEqualTo("Alfa");
        assertThat(stored.notes()).isEqualTo("frágil");
        assertThat(stored.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(stored.createdAt().toInstant()).isEqualTo(createdAt.toInstant());

        repository.deleteById(first);
        repository.deleteById(second);
        assertThat(repository.findAll()).extracting(Order::id).doesNotContain(first, second);
    }
}
