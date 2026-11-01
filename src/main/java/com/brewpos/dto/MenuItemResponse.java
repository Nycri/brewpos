package com.brewpos.dto;

import java.math.BigDecimal;

public record MenuItemResponse(
        Integer itemId,
        Integer categoryId,
        String categoryName,
        String itemName,
        BigDecimal price,
        boolean isActive) {
}
