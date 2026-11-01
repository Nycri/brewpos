package com.brewpos.dto;

import java.util.List;

/** Response shape of GET /api/menu/active: one entry per category that has active items. */
public record ActiveMenuCategoryResponse(
        Integer categoryId,
        String categoryName,
        List<MenuItemResponse> items) {
}
