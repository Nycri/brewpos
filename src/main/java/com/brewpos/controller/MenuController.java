package com.brewpos.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.brewpos.dto.ActiveMenuCategoryResponse;
import com.brewpos.service.MenuQueryService;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuQueryService menuQueryService;

    public MenuController(MenuQueryService menuQueryService) {
        this.menuQueryService = menuQueryService;
    }

    /** Active items grouped by category: [{categoryId, categoryName, items:[...]}]. */
    @GetMapping("/active")
    public List<ActiveMenuCategoryResponse> active() {
        return menuQueryService.activeMenuByCategory();
    }
}
