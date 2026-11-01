package com.brewpos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.CategoryResponse;
import com.brewpos.dto.CreateOrderRequest;
import com.brewpos.dto.MenuItemResponse;
import com.brewpos.dto.OrderDetailResponse;
import com.brewpos.dto.OrderLineRequest;
import com.brewpos.dto.StaffResponse;
import com.brewpos.exception.BusinessRuleException;
import com.brewpos.exception.ConflictException;
import com.brewpos.exception.ResourceNotFoundException;

/**
 * Service-level tests against the real PostgreSQL database. Each test runs in a
 * transaction that is rolled back, so no data is left behind. They need at least
 * one staff row and run when DB_PASSWORD is set in the environment or .env.
 */
@SpringBootTest
@Transactional
@EnabledIf("com.brewpos.DbTestCondition#isDbConfigured")
class BrewPosServiceTests {

    @Autowired MenuCategoryService categoryService;
    @Autowired MenuItemService itemService;
    @Autowired MenuQueryService menuQueryService;
    @Autowired OrderService orderService;
    @Autowired SalesReportService reportService;
    @Autowired StaffService staffService;
    @Autowired JdbcTemplate jdbc;

    private static String unique(String p) {
        return p + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int staffId() {
        List<StaffResponse> staff = staffService.list(null);
        assertThat(staff).as("database needs at least one staff row").isNotEmpty();
        return staff.get(0).staffId();
    }

    private CategoryResponse newCategory() {
        return categoryService.create("  " + unique("Cat") + "  ");
    }

    private MenuItemResponse newItem(CategoryResponse c, String price) {
        return itemService.create(c.categoryId(), unique("Item"), new BigDecimal(price), true);
    }

    @Test
    void categoryCrudTrimAndDuplicate() {
        CategoryResponse c = newCategory();
        assertThat(c.categoryName()).doesNotStartWith(" ").doesNotEndWith(" ");
        assertThat(categoryService.list()).extracting(CategoryResponse::categoryId).contains(c.categoryId());
        String renamed = unique("Renamed");
        assertThat(categoryService.update(c.categoryId(), renamed).categoryName()).isEqualTo(renamed);
        assertThatThrownBy(() -> categoryService.create(renamed)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> categoryService.create("   ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> categoryService.create("x".repeat(51))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> categoryService.get(-1)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        CategoryResponse c = newCategory();
        newItem(c, "10.00");
        assertThatThrownBy(() -> categoryService.delete(c.categoryId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void menuItemRules() {
        CategoryResponse a = newCategory();
        CategoryResponse b = newCategory();
        String name = unique("Latte");
        itemService.create(a.categoryId(), name, new BigDecimal("5.00"), null);
        // default active
        assertThat(itemService.list(true)).extracting(MenuItemResponse::itemName).contains(name);
        // duplicate in same category -> conflict; same name in other category allowed
        assertThatThrownBy(() -> itemService.create(a.categoryId(), name, BigDecimal.ONE, true))
                .isInstanceOf(ConflictException.class);
        itemService.create(b.categoryId(), name, BigDecimal.ONE, true);
        // invalid category, negative price, too many decimals
        assertThatThrownBy(() -> itemService.create(-1, "x", BigDecimal.ONE, true))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> itemService.create(a.categoryId(), "neg", new BigDecimal("-1"), true))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> itemService.create(a.categoryId(), "dec", new BigDecimal("1.234"), true))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> itemService.create(a.categoryId(), "big", new BigDecimal("100000000.00"), true))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void inactiveItemsExcludedFromActiveMenu() {
        CategoryResponse c = newCategory();
        MenuItemResponse item = newItem(c, "12.00");
        itemService.setActive(item.itemId(), false);
        assertThat(menuQueryService.activeMenuByCategory())
                .flatExtracting(g -> g.items()).extracting(MenuItemResponse::itemId)
                .doesNotContain(item.itemId());
        itemService.setActive(item.itemId(), true);
        assertThat(menuQueryService.activeMenuByCategory())
                .flatExtracting(g -> g.items()).extracting(MenuItemResponse::itemId)
                .contains(item.itemId());
    }

    @Test
    void orderCalculatesTotalMergesDuplicatesAndKeepsHistoricalPrice() {
        CategoryResponse c = newCategory();
        MenuItemResponse a = newItem(c, "100.00");
        MenuItemResponse b = newItem(c, "75.50");
        // 2 x 100.00 (as 1 + 1, merged) + 1 x 75.50 = 275.50
        OrderDetailResponse o = orderService.create(new CreateOrderRequest(staffId(), "CASH", List.of(
                new OrderLineRequest(a.itemId(), 1), new OrderLineRequest(b.itemId(), 1),
                new OrderLineRequest(a.itemId(), 1))));
        assertThat(o.lines()).hasSize(2);
        assertThat(o.total()).isEqualByComparingTo("275.50");
        assertThat(o.orderTimestamp()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from order_line_item where order_id = ?",
                Integer.class, o.orderId())).isEqualTo(2);

        // change current price: historical sold_price and total stay the same
        itemService.update(a.itemId(), c.categoryId(), a.itemName(), new BigDecimal("999.00"), true);
        OrderDetailResponse again = orderService.get(o.orderId());
        assertThat(again.total()).isEqualByComparingTo("275.50");
        assertThat(again.lines()).anySatisfy(l -> assertThat(l.soldPrice()).isEqualByComparingTo("100.00"));
        assertThat(orderService.list(null, null)).extracting(r -> r.orderId()).contains(o.orderId());
    }

    @Test
    void invalidOrdersAreRejected() {
        CategoryResponse c = newCategory();
        MenuItemResponse item = newItem(c, "10.00");
        int staff = staffId();
        List<OrderLineRequest> ok = List.of(new OrderLineRequest(item.itemId(), 1));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(-1, "CASH", ok)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CARD", ok)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "cash", ok)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CASH", List.of())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CASH",
                List.of(new OrderLineRequest(item.itemId(), 0)))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CASH",
                List.of(new OrderLineRequest(item.itemId(), -2)))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CASH",
                List.of(new OrderLineRequest(-1, 1)))))
                .isInstanceOf(ResourceNotFoundException.class);
        itemService.setActive(item.itemId(), false);
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(staff, "CASH", ok)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> orderService.get(-1)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dailyReportsMatchManualSqlAndDoNotInflate() {
        CategoryResponse c = newCategory();
        MenuItemResponse a = newItem(c, "10.00");
        MenuItemResponse b = newItem(c, "2.50");
        int staff = staffId();
        LocalDate today = jdbc.queryForObject("select current_date", LocalDate.class);

        var before = reportService.dailySales(today);
        var beforePay = reportService.salesByPayment(today);

        orderService.create(new CreateOrderRequest(staff, "CASH", List.of(
                new OrderLineRequest(a.itemId(), 2), new OrderLineRequest(b.itemId(), 2)))); // 25.00
        orderService.create(new CreateOrderRequest(staff, "ONLINE", List.of(
                new OrderLineRequest(a.itemId(), 1)))); // 10.00

        var after = reportService.dailySales(today);
        assertThat(after.totalSales().subtract(before.totalSales())).isEqualByComparingTo("35.00");
        assertThat(after.orderCount() - before.orderCount()).isEqualTo(2);

        var afterPay = reportService.salesByPayment(today);
        assertThat(afterPay).hasSize(2);
        BigDecimal sum = afterPay.stream().map(p -> p.totalSales()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(after.totalSales());

        BigDecimal manual = jdbc.queryForObject("select coalesce(sum(l.quantity * l.sold_price), 0) "
                + "from order_ticket t join order_line_item l on l.order_id = t.order_id "
                + "where t.order_timestamp >= ? and t.order_timestamp < ?",
                BigDecimal.class, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        assertThat(after.totalSales()).isEqualByComparingTo(manual);
        assertThat(beforePay).hasSize(2);
    }

    @Test
    void reportForEmptyFutureDateIsZero() {
        var r = reportService.dailySales(LocalDate.of(2999, 1, 1));
        assertThat(r.totalSales()).isEqualByComparingTo("0.00");
        assertThat(r.orderCount()).isZero();
    }
}
