package com.brewpos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMenuItemRequest(
        @NotNull(message = "Category is required")
        Integer categoryId,
        @NotBlank(message = "Item name is required")
        @Size(max = 100, message = "Item name must be at most 100 characters")
        String itemName,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must not be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places (max 8 integer digits)")
        BigDecimal price,
        @NotNull(message = "isActive is required")
        Boolean isActive) {
}
