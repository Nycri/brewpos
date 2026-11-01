package com.brewpos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Order header, lines, line subtotals, and derived total. */
public record OrderDetailResponse(
        Integer orderId,
        LocalDateTime orderTimestamp,
        Integer staffId,
        String staffName,
        String paymentMethod,
        List<OrderLineResponse> lines,
        BigDecimal total) {
}
