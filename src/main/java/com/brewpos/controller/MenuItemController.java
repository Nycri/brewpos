package com.brewpos.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.brewpos.dto.CreateMenuItemRequest;
import com.brewpos.dto.MenuItemResponse;
import com.brewpos.dto.UpdateActiveRequest;
import com.brewpos.dto.UpdateMenuItemRequest;
import com.brewpos.service.MenuItemService;

import jakarta.validation.Valid;

/** No DELETE endpoint: items are deactivated (PATCH .../active) to preserve order history. */
@RestController
@RequestMapping("/api/menu-items")
public class MenuItemController {

    private final MenuItemService itemService;

    public MenuItemController(MenuItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<MenuItemResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return itemService.list(activeOnly);
    }

    @GetMapping("/{id}")
    public MenuItemResponse get(@PathVariable Integer id) {
        return itemService.get(id);
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody CreateMenuItemRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemService.create(r.categoryId(), r.itemName(), r.price(), r.isActive()));
    }

    @PutMapping("/{id}")
    public MenuItemResponse update(@PathVariable Integer id, @Valid @RequestBody UpdateMenuItemRequest r) {
        return itemService.update(id, r.categoryId(), r.itemName(), r.price(), r.isActive());
    }

    @PatchMapping("/{id}/active")
    public MenuItemResponse setActive(@PathVariable Integer id, @Valid @RequestBody UpdateActiveRequest r) {
        return itemService.setActive(id, r.isActive());
    }
}
