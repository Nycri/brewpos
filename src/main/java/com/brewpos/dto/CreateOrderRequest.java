package com.brewpos.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body of POST /api/orders. Unknown JSON properties such as a price or
 * total are not part of this record and are therefore ignored.
 */
public record CreateOrderRequest(
        @NotNull(message = "Staff ID is required")
        Integer staffId,
        @NotNull(message = "Payment method is required")
        @Pattern(regexp = "CASH|ONLINE", message = "Payment method must be CASH or ONLINE")
        String paymentMethod,
        @NotEmpty(message = "An order must contain at least one item")
        List<@NotNull(message = "Order line must not be null") @Valid OrderLineRequest> items) {
}
