package com.brewpos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.brewpos.dto.ActiveMenuCategoryResponse;
import com.brewpos.dto.CategoryResponse;
import com.brewpos.dto.CreateOrderRequest;
import com.brewpos.dto.DailySalesResponse;
import com.brewpos.dto.MenuItemResponse;
import com.brewpos.dto.OrderDetailResponse;
import com.brewpos.dto.OrderLineRequest;
import com.brewpos.dto.OrderLineResponse;
import com.brewpos.dto.OrderResponse;
import com.brewpos.dto.PaymentMethodSalesResponse;
import com.brewpos.dto.StaffResponse;
import com.brewpos.exception.ConflictException;
import com.brewpos.exception.ResourceNotFoundException;
import com.brewpos.service.MenuCategoryService;
import com.brewpos.service.MenuItemService;
import com.brewpos.service.MenuQueryService;
import com.brewpos.service.OrderService;
import com.brewpos.service.SalesReportService;
import com.brewpos.service.StaffService;

/**
 * HTTP / WebMVC slice tests for all 6 REST controllers, Bean Validation (@Valid),
 * status codes (200, 201, 204, 400, 404, 405, 409, 415, 500), and GlobalExceptionHandler.
 * Services are mocked, so these tests run without requiring DB_PASSWORD.
 */
@WebMvcTest
class BrewPosControllerTests {

    @Autowired MockMvc mvc;

    @MockitoBean StaffService staffService;
    @MockitoBean MenuCategoryService categoryService;
    @MockitoBean MenuItemService itemService;
    @MockitoBean MenuQueryService menuQueryService;
    @MockitoBean OrderService orderService;
    @MockitoBean SalesReportService reportService;

    @Test
    void staffEndpointsAndNotFoundErrorShape() throws Exception {
        when(staffService.list("CASHIER")).thenReturn(List.of(new StaffResponse(1, "Demo Cashier", "CASHIER")));
        mvc.perform(get("/api/staff").param("role", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].staffId").value(1))
                .andExpect(jsonPath("$[0].role").value("CASHIER"));

        when(staffService.get(999)).thenThrow(new ResourceNotFoundException("Staff 999 not found"));
        mvc.perform(get("/api/staff/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Staff 999 not found"))
                .andExpect(jsonPath("$.path").value("/api/staff/999"));
    }

    @Test
    void categoryCrudValidationAndConflicts() throws Exception {
        when(categoryService.create("Coffee")).thenReturn(new CategoryResponse(1, "Coffee"));
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Coffee\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.categoryName").value("Coffee"));

        // Blank name rejected by @Valid -> 400
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Duplicate name -> 409
        when(categoryService.create("Coffee"))
                .thenThrow(new ConflictException("A category named 'Coffee' already exists"));
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Coffee\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Safe delete -> 204 when unused, 409 when referenced by items
        mvc.perform(delete("/api/categories/2"))
                .andExpect(status().isNoContent());

        doThrow(new ConflictException("Category is still in use by menu items and cannot be deleted"))
                .when(categoryService).delete(1);
        mvc.perform(delete("/api/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void menuItemCrudActivationAndNoHardDelete() throws Exception {
        MenuItemResponse created = new MenuItemResponse(5, 1, "Coffee", "Americano",
                new BigDecimal("90.00"), true);
        when(itemService.create(1, "Americano", new BigDecimal("90.00"), null)).thenReturn(created);

        mvc.perform(post("/api/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"itemName\":\"Americano\",\"price\":90.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemId").value(5))
                .andExpect(jsonPath("$.isActive").value(true));

        // Negative price and 3 decimals rejected by @Valid -> 400
        mvc.perform(post("/api/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"itemName\":\"Bad\",\"price\":-1.00}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"itemName\":\"Bad\",\"price\":12.345}"))
                .andExpect(status().isBadRequest());

        // Activation toggle via PATCH -> 200
        MenuItemResponse deactivated = new MenuItemResponse(5, 1, "Coffee", "Americano",
                new BigDecimal("90.00"), false);
        when(itemService.setActive(5, false)).thenReturn(deactivated);
        mvc.perform(patch("/api/menu-items/5/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Hard delete is intentionally unsupported -> 405 Method Not Allowed (not 500)
        mvc.perform(delete("/api/menu-items/5"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void activeMenuGroupedByCategory() throws Exception {
        MenuItemResponse latte = new MenuItemResponse(2, 1, "Coffee", "Cafe Latte",
                new BigDecimal("120.00"), true);
        when(menuQueryService.activeMenuByCategory())
                .thenReturn(List.of(new ActiveMenuCategoryResponse(1, "Coffee", List.of(latte))));

        mvc.perform(get("/api/menu/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("Coffee"))
                .andExpect(jsonPath("$[0].items[0].itemName").value("Cafe Latte"));
    }

    @Test
    void createOrderIgnoresClientPriceAndTotalAndReturns201() throws Exception {
        OrderDetailResponse saved = new OrderDetailResponse(
                10,
                LocalDateTime.of(2026, 11, 1, 10, 30),
                1,
                "Demo Cashier",
                "CASH",
                List.of(new OrderLineResponse(2, "Americano", 2, new BigDecimal("90.00"), new BigDecimal("180.00"))),
                new BigDecimal("180.00"));
        when(orderService.create(any(CreateOrderRequest.class))).thenReturn(saved);

        // Client attempts to sneak in price=0.01 and total=0.02
        String maliciousJson = """
                {
                  "staffId": 1,
                  "paymentMethod": "CASH",
                  "total": 0.02,
                  "items": [
                    { "itemId": 2, "quantity": 2, "price": 0.01, "soldPrice": 0.01 }
                  ]
                }
                """;

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.lines[0].soldPrice").value(90.00))
                .andExpect(jsonPath("$.total").value(180.00));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).create(captor.capture());
        assertThat(captor.getValue().staffId()).isEqualTo(1);
        assertThat(captor.getValue().paymentMethod()).isEqualTo("CASH");
        assertThat(captor.getValue().items()).containsExactly(new OrderLineRequest(2, 2));
    }

    @Test
    void invalidOrderRequestsRejectedWith400AndDeleteOrderReturns405() throws Exception {
        // Lowercase payment method
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":1,\"paymentMethod\":\"cash\",\"items\":[{\"itemId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        // Empty items array
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":1,\"paymentMethod\":\"CASH\",\"items\":[]}"))
                .andExpect(status().isBadRequest());

        // Quantity <= 0
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":1,\"paymentMethod\":\"CASH\",\"items\":[{\"itemId\":1,\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());

        // Malformed JSON
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest());

        // Completed orders are immutable -> DELETE returns 405
        mvc.perform(delete("/api/orders/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void orderHistoryAndSalesReportsEndpoints() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 1);
        when(orderService.list(d, d)).thenReturn(List.of(
                new OrderResponse(10, LocalDateTime.of(2026, 11, 1, 10, 30), 1, "Demo Cashier", "CASH",
                        new BigDecimal("180.00"))));

        mvc.perform(get("/api/orders").param("from", "2026-11-01").param("to", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[0].total").value(180.00));

        when(reportService.dailySales(d))
                .thenReturn(new DailySalesResponse(d, new BigDecimal("915.50"), 3));
        mvc.perform(get("/api/reports/daily-sales").param("date", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales").value(915.50))
                .andExpect(jsonPath("$.orderCount").value(3));

        when(reportService.salesByPayment(d)).thenReturn(List.of(
                new PaymentMethodSalesResponse("CASH", new BigDecimal("615.50"), 2),
                new PaymentMethodSalesResponse("ONLINE", new BigDecimal("300.00"), 1)));
        mvc.perform(get("/api/reports/daily-sales/by-payment").param("date", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$[1].paymentMethod").value("ONLINE"));

        // Missing or malformed date -> 400
        mvc.perform(get("/api/reports/daily-sales"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/daily-sales").param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPathReturns404AndUnexpectedErrorSanitizes500() throws Exception {
        mvc.perform(get("/api/unknown-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Wrong Content-Type is a client error (415), not a server failure (500)
        mvc.perform(post("/api/categories")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Coffee"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        when(categoryService.list()).thenThrow(new RuntimeException("secret JDBC detail"));
        mvc.perform(get("/api/categories"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void staticFrontendShellAndModulesAreServed() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BrewPOS")));

        for (String asset : List.of(
                "/css/styles.css",
                "/js/api.js",
                "/js/pos.js",
                "/js/menu.js",
                "/js/orders.js",
                "/js/reports.js",
                "/js/app.js")) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
    }
}

