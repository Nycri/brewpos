package com.brewpos.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.ActiveMenuCategoryResponse;
import com.brewpos.dto.MenuItemResponse;
import com.brewpos.entity.MenuItem;
import com.brewpos.repository.MenuItemRepository;

/** Cashier menu: active items grouped by category. */
@Service
@Transactional(readOnly = true)
public class MenuQueryService {

    private final MenuItemRepository itemRepository;

    public MenuQueryService(MenuItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public List<ActiveMenuCategoryResponse> activeMenuByCategory() {
        Map<Integer, List<MenuItemResponse>> grouped = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (MenuItem item : itemRepository.findActiveWithCategory()) {
            Integer cid = item.getCategory().getCategoryId();
            names.putIfAbsent(cid, item.getCategory().getCategoryName());
            grouped.computeIfAbsent(cid, k -> new ArrayList<>()).add(MenuItemService.toResponse(item));
        }
        List<ActiveMenuCategoryResponse> result = new ArrayList<>();
        grouped.forEach((cid, items) -> result.add(new ActiveMenuCategoryResponse(cid, names.get(cid), items)));
        return result;
    }
}
