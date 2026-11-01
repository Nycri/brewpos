package com.brewpos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.brewpos.entity.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {

    boolean existsByCategoryCategoryId(Integer categoryId);

    boolean existsByCategoryCategoryIdAndItemName(Integer categoryId, String itemName);

    boolean existsByCategoryCategoryIdAndItemNameAndItemIdNot(Integer categoryId, String itemName, Integer itemId);

    /** All items with category loaded in one query (avoids N+1). */
    @Query("select i from MenuItem i join fetch i.category c order by c.categoryName, i.itemName")
    List<MenuItem> findAllWithCategory();

    /** Active items only, category loaded in one query. */
    @Query("select i from MenuItem i join fetch i.category c where i.active = true "
            + "order by c.categoryName, i.itemName")
    List<MenuItem> findActiveWithCategory();

    @Query("select i from MenuItem i join fetch i.category where i.itemId = :id")
    Optional<MenuItem> findByIdWithCategory(@Param("id") Integer id);
}
