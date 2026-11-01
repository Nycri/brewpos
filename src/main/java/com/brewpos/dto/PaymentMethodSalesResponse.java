package com.brewpos.dto;

import java.math.BigDecimal;

public record PaymentMethodSalesResponse(String paymentMethod, BigDecimal totalSales, long orderCount) {
}
