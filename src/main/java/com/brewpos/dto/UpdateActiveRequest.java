package com.brewpos.dto;

import jakarta.validation.constraints.NotNull;

/** Body of PATCH /api/menu-items/{id}/active. */
public record UpdateActiveRequest(
        @NotNull(message = "isActive is required")
        Boolean isActive) {
}
