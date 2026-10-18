package com.foody.delivery.rpd.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = { "address", "client", "items" })
    Page<Order> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "address", "client", "items" })
    Optional<Order> findById(Long id);

    @EntityGraph(attributePaths = { "address", "client", "items" })
    Page<Order> findByClientId(Long clientId, Pageable pageable);
}
