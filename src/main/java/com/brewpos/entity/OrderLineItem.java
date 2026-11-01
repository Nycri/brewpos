package com.brewpos.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Maps table {@code order_line_item}: composite key (order_id, item_id), no surrogate id.
 * {@code soldPrice} is the unit price at time of sale and is never modified afterwards.
 */
@Entity
@Table(name = "order_line_item")
public class OrderLineItem {

    @EmbeddedId
    private OrderLineItemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("orderId")
    @JoinColumn(name = "order_id", nullable = false)
    private OrderTicket order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("itemId")
    @JoinColumn(name = "item_id", nullable = false)
    private MenuItem item;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "sold_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal soldPrice;

    protected OrderLineItem() {
    }

    public OrderLineItem(OrderTicket order, MenuItem item, Integer quantity, BigDecimal soldPrice) {
        this.id = new OrderLineItemId(order.getOrderId(), item.getItemId());
        this.order = order;
        this.item = item;
        this.quantity = quantity;
        this.soldPrice = soldPrice != null ? soldPrice.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public OrderLineItemId getId() { return id; }
    public OrderTicket getOrder() { return order; }
    public MenuItem getItem() { return item; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getSoldPrice() { return soldPrice; }

    /** Derived value, never persisted: quantity x sold_price. */
    public BigDecimal getSubtotal() {
        return soldPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
