package com.brewpos.dto;

import java.math.BigDecimal;

public record OrderLineResponse(
        Integer itemId,
        String itemName,
        Integer quantity,
        BigDecimal soldPrice,
        BigDecimal subtotal) {
}
