package com.brewpos.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.brewpos.entity.OrderLineItem;
import com.brewpos.entity.OrderLineItemId;

public interface OrderLineItemRepository extends JpaRepository<OrderLineItem, OrderLineItemId> {

    /** Lines of one order with item loaded (single query). */
    @Query("select l from OrderLineItem l join fetch l.item where l.id.orderId = :orderId order by l.id.itemId")
    List<OrderLineItem> findByOrderId(@Param("orderId") Integer orderId);

    /** Batch fetch for many orders at once (avoids one query per order). */
    @Query("select l from OrderLineItem l join fetch l.item where l.id.orderId in :orderIds")
    List<OrderLineItem> findByOrderIds(@Param("orderIds") Collection<Integer> orderIds);

    /**
     * Daily totals computed straight from line items, each line counted once
     * (no order-header join fan-out). Row: [total (BigDecimal|null), distinct orders (Long)].
     * Range is half-open: from inclusive, to exclusive.
     */
    @Query("select sum(l.quantity * l.soldPrice), count(distinct t.orderId) "
            + "from OrderLineItem l join l.order t "
            + "where t.orderTimestamp >= :from and t.orderTimestamp < :to")
    List<Object[]> dailyTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Rows: [payment_method, total, distinct orders]. */
    @Query("select t.paymentMethod, sum(l.quantity * l.soldPrice), count(distinct t.orderId) "
            + "from OrderLineItem l join l.order t "
            + "where t.orderTimestamp >= :from and t.orderTimestamp < :to "
            + "group by t.paymentMethod")
    List<Object[]> totalsByPaymentMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
