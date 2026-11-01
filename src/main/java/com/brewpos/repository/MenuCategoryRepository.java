package com.brewpos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewpos.entity.MenuCategory;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Integer> {

    boolean existsByCategoryName(String categoryName);

    boolean existsByCategoryNameAndCategoryIdNot(String categoryName, Integer categoryId);

    List<MenuCategory> findAllByOrderByCategoryName();
}
