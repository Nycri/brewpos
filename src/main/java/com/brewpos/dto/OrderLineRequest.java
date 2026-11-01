package com.brewpos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** No price or total: the server always reads the price from the database. */
public record OrderLineRequest(
        @NotNull(message = "Item ID is required")
        Integer itemId,
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be greater than zero")
        Integer quantity) {
}
