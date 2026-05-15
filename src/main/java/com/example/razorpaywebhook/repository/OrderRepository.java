package com.example.razorpaywebhook.repository;

import com.example.razorpaywebhook.domain.entity.Order;
import com.example.razorpaywebhook.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String key);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus " +
            "WHERE o.id = :id AND o.status = :expectedStatus")
    int updateStatusConditionally(@Param("id") UUID id,
                                  @Param("newStatus") OrderStatus newStatus,
                                  @Param("expectedStatus") OrderStatus expectedStatus);
}