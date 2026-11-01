package com.brewpos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.brewpos.dto.CategoryResponse;
import com.brewpos.dto.CreateOrderRequest;
import com.brewpos.dto.MenuItemResponse;
import com.brewpos.dto.OrderLineRequest;
import com.brewpos.repository.OrderLineItemRepository;

/**
 * Proves that a failure while saving a line rolls back the ticket and all lines.
 * NOT wrapped in a test transaction (the service's own transaction must commit or
 * roll back for real), so it creates and then removes its own uniquely named data.
 */
@SpringBootTest
@EnabledIf("com.brewpos.DbTestCondition#isDbConfigured")
class OrderRollbackTests {

    @Autowired MenuCategoryService categoryService;
    @Autowired MenuItemService itemService;
    @Autowired OrderService orderService;
    @Autowired StaffService staffService;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean OrderLineItemRepository lineRepository;

    @Test
    void failureWhileSavingSecondLineLeavesNoTicketAndNoLines() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        CategoryResponse cat = categoryService.create("RB-" + tag);
        try {
            MenuItemResponse a = itemService.create(cat.categoryId(), "A-" + tag, new BigDecimal("10.00"), true);
            MenuItemResponse b = itemService.create(cat.categoryId(), "B-" + tag, new BigDecimal("20.00"), true);
            int staff = staffService.list(null).get(0).staffId();

            int ticketsBefore = count("order_ticket");
            int linesBefore = count("order_line_item");

            // Simulate failure during line persisting/flushing; the ticket was already
            // flushed in step 10, so this verifies that the transaction properly rolls back
            // both the order_ticket and all line items.
            doThrow(new IllegalStateException("simulated failure"))
                    .when(lineRepository).flush();

            assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CASH", List.of(
                    new OrderLineRequest(a.itemId(), 1), new OrderLineRequest(b.itemId(), 1)))))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(count("order_ticket")).as("ticket rolled back").isEqualTo(ticketsBefore);
            assertThat(count("order_line_item")).as("lines rolled back").isEqualTo(linesBefore);
        } finally {
            reset(lineRepository);
            jdbc.update("delete from menu_item where category_id = ?", cat.categoryId());
            jdbc.update("delete from menu_category where category_id = ?", cat.categoryId());
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
