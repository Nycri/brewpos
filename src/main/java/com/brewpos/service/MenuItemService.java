package com.brewpos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.MenuItemResponse;
import com.brewpos.entity.MenuCategory;
import com.brewpos.entity.MenuItem;
import com.brewpos.exception.BusinessRuleException;
import com.brewpos.exception.ConflictException;
import com.brewpos.exception.ResourceNotFoundException;
import com.brewpos.repository.MenuCategoryRepository;
import com.brewpos.repository.MenuItemRepository;

/**
 * Menu item maintenance. Changing a price here never touches order_line_item.sold_price.
 * Hard deletion is intentionally not offered: items are deactivated to preserve history.
 */
@Service
@Transactional(readOnly = true)
public class MenuItemService {

    private static final int MAX_NAME = 100;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final MenuItemRepository itemRepository;
    private final MenuCategoryRepository categoryRepository;

    public MenuItemService(MenuItemRepository itemRepository, MenuCategoryRepository categoryRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<MenuItemResponse> list(boolean activeOnly) {
        List<MenuItem> items = activeOnly ? itemRepository.findActiveWithCategory() : itemRepository.findAllWithCategory();
        return items.stream().map(MenuItemService::toResponse).toList();
    }

    public MenuItemResponse get(Integer id) {
        return toResponse(find(id));
    }

    @Transactional
    public MenuItemResponse create(Integer categoryId, String rawName, BigDecimal price, Boolean isActive) {
        MenuCategory category = findCategory(categoryId);
        String name = cleanName(rawName);
        BigDecimal normalizedPrice = validatePrice(price);
        if (itemRepository.existsByCategoryCategoryIdAndItemName(categoryId, name)) {
            throw duplicate(name);
        }
        MenuItem item = new MenuItem(category, name, normalizedPrice, isActive == null || isActive);
        return toResponse(saveAndFlush(item, name));
    }

    @Transactional
    public MenuItemResponse update(Integer id, Integer categoryId, String rawName, BigDecimal price, Boolean isActive) {
        MenuItem item = find(id);
        MenuCategory category = findCategory(categoryId);
        String name = cleanName(rawName);
        BigDecimal normalizedPrice = validatePrice(price);
        if (isActive == null) {
            throw new BusinessRuleException("isActive is required");
        }
        if (itemRepository.existsByCategoryCategoryIdAndItemNameAndItemIdNot(categoryId, name, id)) {
            throw duplicate(name);
        }
        item.setCategory(category);
        item.setItemName(name);
        item.setPrice(normalizedPrice);
        item.setActive(isActive);
        return toResponse(saveAndFlush(item, name));
    }

    @Transactional
    public MenuItemResponse setActive(Integer id, boolean active) {
        MenuItem item = find(id);
        item.setActive(active);
        return toResponse(itemRepository.saveAndFlush(item));
    }

    private MenuItem saveAndFlush(MenuItem item, String name) {
        try {
            return itemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            throw duplicate(name);
        }
    }

    private MenuItem find(Integer id) {
        return itemRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item " + id + " not found"));
    }

    private MenuCategory findCategory(Integer categoryId) {
        if (categoryId == null) {
            throw new BusinessRuleException("Category is required");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + categoryId + " not found"));
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("An item named '" + name + "' already exists in this category");
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BusinessRuleException("Item name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new BusinessRuleException("Item name must be at most 100 characters");
        }
        return name;
    }

    private static BigDecimal validatePrice(BigDecimal price) {
        if (price == null) {
            throw new BusinessRuleException("Price is required");
        }
        if (price.signum() < 0) {
            throw new BusinessRuleException("Price must not be negative");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Price must have at most 2 decimal places");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new BusinessRuleException("Price must not exceed 99999999.99");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    static MenuItemResponse toResponse(MenuItem i) {
        return new MenuItemResponse(i.getItemId(), i.getCategory().getCategoryId(),
                i.getCategory().getCategoryName(), i.getItemName(), i.getPrice(), i.isActive());
    }
}
