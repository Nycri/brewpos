package com.brewpos.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.CategoryResponse;
import com.brewpos.entity.MenuCategory;
import com.brewpos.exception.BusinessRuleException;
import com.brewpos.exception.ConflictException;
import com.brewpos.exception.ResourceNotFoundException;
import com.brewpos.repository.MenuCategoryRepository;
import com.brewpos.repository.MenuItemRepository;

@Service
@Transactional(readOnly = true)
public class MenuCategoryService {

    private static final int MAX_NAME = 50;
    private static final String CATEGORY_IN_USE = "Category is still in use by menu items and cannot be deleted";

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    public MenuCategoryService(MenuCategoryRepository categoryRepository, MenuItemRepository itemRepository) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByCategoryName().stream().map(MenuCategoryService::toResponse).toList();
    }

    public CategoryResponse get(Integer id) {
        return toResponse(find(id));
    }

    @Transactional
    public CategoryResponse create(String rawName) {
        String name = cleanName(rawName);
        if (categoryRepository.existsByCategoryName(name)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        try {
            return toResponse(categoryRepository.saveAndFlush(new MenuCategory(name)));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
    }

    @Transactional
    public CategoryResponse update(Integer id, String rawName) {
        MenuCategory category = find(id);
        String name = cleanName(rawName);
        if (categoryRepository.existsByCategoryNameAndCategoryIdNot(name, id)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        category.setCategoryName(name);
        try {
            return toResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
    }

    /** Deletes only a category with no menu items; never touches orders. */
    @Transactional
    public void delete(Integer id) {
        MenuCategory category = find(id);
        if (itemRepository.existsByCategoryCategoryId(id)) {
            throw new ConflictException(CATEGORY_IN_USE);
        }
        try {
            categoryRepository.delete(category);
            // Flush now so a concurrent item insert (FK violation) is reported as 409 here.
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(CATEGORY_IN_USE);
        }
    }

    private MenuCategory find(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    /** Trims whitespace only; case is preserved. */
    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BusinessRuleException("Category name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new BusinessRuleException("Category name must be at most 50 characters");
        }
        return name;
    }

    private static CategoryResponse toResponse(MenuCategory c) {
        return new CategoryResponse(c.getCategoryId(), c.getCategoryName());
    }
}
