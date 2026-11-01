package com.brewpos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.brewpos.entity.OrderTicket;

public interface OrderTicketRepository extends JpaRepository<OrderTicket, Integer> {

    /** Newest first, staff loaded in the same query. */
    @Query("select t from OrderTicket t join fetch t.staff order by t.orderTimestamp desc, t.orderId desc")
    List<OrderTicket> findAllNewestFirst();

    /** Half-open range: from inclusive, to exclusive. */
    @Query("select t from OrderTicket t join fetch t.staff "
            + "where t.orderTimestamp >= :from and t.orderTimestamp < :to "
            + "order by t.orderTimestamp desc, t.orderId desc")
    List<OrderTicket> findInRangeNewestFirst(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select t from OrderTicket t join fetch t.staff where t.orderId = :id")
    Optional<OrderTicket> findByIdWithStaff(@Param("id") Integer id);
}
