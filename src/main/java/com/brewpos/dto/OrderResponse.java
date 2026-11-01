package com.brewpos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Order history row: header plus derived total. */
public record OrderResponse(
        Integer orderId,
        LocalDateTime orderTimestamp,
        Integer staffId,
        String staffName,
        String paymentMethod,
        BigDecimal total) {
}
