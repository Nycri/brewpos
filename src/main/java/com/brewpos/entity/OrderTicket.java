package com.brewpos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Maps table {@code order_ticket} (not "order": ORDER is a SQL keyword).
 * No total/status columns exist; totals are derived from order lines.
 */
@Entity
@Table(name = "order_ticket")
public class OrderTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    /**
     * Left to the database default (CURRENT_TIMESTAMP). Stored as TIMESTAMP without
     * time zone and treated as local cafe time. Populated by refreshing after insert.
     */
    @Column(name = "order_timestamp", nullable = false, insertable = false, updatable = false)
    private LocalDateTime orderTimestamp;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    protected OrderTicket() {
    }

    public OrderTicket(Staff staff, String paymentMethod) {
        this.staff = staff;
        this.paymentMethod = paymentMethod;
    }

    public Integer getOrderId() { return orderId; }
    public Staff getStaff() { return staff; }
    public LocalDateTime getOrderTimestamp() { return orderTimestamp; }
    public String getPaymentMethod() { return paymentMethod; }
}
