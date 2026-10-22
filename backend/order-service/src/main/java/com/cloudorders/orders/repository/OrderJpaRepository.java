package com.cloudorders.orders.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByOrderByIdAsc();

    @Query(value = "select nextval('orders_id_seq')", nativeQuery = true)
    long nextId();
}
