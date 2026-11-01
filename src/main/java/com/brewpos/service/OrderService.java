package com.brewpos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.CreateOrderRequest;
import com.brewpos.dto.OrderDetailResponse;
import com.brewpos.dto.OrderLineRequest;
import com.brewpos.dto.OrderLineResponse;
import com.brewpos.dto.OrderResponse;
import com.brewpos.entity.MenuItem;
import com.brewpos.entity.OrderLineItem;
import com.brewpos.entity.OrderTicket;
import com.brewpos.entity.Staff;
import com.brewpos.exception.BusinessRuleException;
import com.brewpos.exception.ResourceNotFoundException;
import com.brewpos.repository.MenuItemRepository;
import com.brewpos.repository.OrderLineItemRepository;
import com.brewpos.repository.OrderTicketRepository;
import com.brewpos.repository.StaffRepository;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "ONLINE");

    private final StaffRepository staffRepository;
    private final MenuItemRepository itemRepository;
    private final OrderTicketRepository ticketRepository;
    private final OrderLineItemRepository lineRepository;
    private final EntityManager entityManager;

    public OrderService(StaffRepository staffRepository, MenuItemRepository itemRepository,
            OrderTicketRepository ticketRepository, OrderLineItemRepository lineRepository,
            EntityManager entityManager) {
        this.staffRepository = staffRepository;
        this.itemRepository = itemRepository;
        this.ticketRepository = ticketRepository;
        this.lineRepository = lineRepository;
        this.entityManager = entityManager;
    }

    /**
     * Creates the ticket and ALL its lines in one transaction. Any failure rolls back
     * everything, so no empty/partial order remains. Prices come only from the database.
     */
    @Transactional
    public OrderDetailResponse create(CreateOrderRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Order request is required");
        }
        // 1. staff must exist
        if (request.staffId() == null) {
            throw new BusinessRuleException("Staff ID is required");
        }
        Staff staff = staffRepository.findById(request.staffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff " + request.staffId() + " not found"));

        // 2. payment method exactly CASH / ONLINE
        String payment = request.paymentMethod();
        if (payment == null || !PAYMENT_METHODS.contains(payment)) {
            throw new BusinessRuleException("Payment method must be CASH or ONLINE");
        }

        // 3-6. lines present, valid, duplicates merged (composite key forbids duplicates)
        List<OrderLineRequest> lines = request.items();
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("An order must contain at least one item");
        }
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : lines) {
            if (line == null || line.itemId() == null) {
                throw new BusinessRuleException("Item ID is required for every order line");
            }
            if (line.quantity() == null || line.quantity() <= 0) {
                throw new BusinessRuleException("Quantity must be greater than zero");
            }
            try {
                quantities.merge(line.itemId(), line.quantity(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new BusinessRuleException("Quantity is too large");
            }
        }

        // 7-9. fetch all items in one query; must exist and be active
        Map<Integer, MenuItem> items = itemRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(MenuItem::getItemId, i -> i));
        for (Integer itemId : quantities.keySet()) {
            MenuItem item = items.get(itemId);
            if (item == null) {
                throw new ResourceNotFoundException("Menu item " + itemId + " not found");
            }
            if (!item.isActive()) {
                throw new BusinessRuleException("Menu item '" + item.getItemName() + "' is not active");
            }
        }

        // 10. ticket (timestamp from DB default, loaded back via refresh)
        OrderTicket ticket = ticketRepository.saveAndFlush(new OrderTicket(staff, payment));
        entityManager.refresh(ticket);

        // 11. one line per distinct item with the server-derived sold price
        List<OrderLineItem> saved = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : quantities.entrySet()) {
            MenuItem item = items.get(e.getKey());
            saved.add(lineRepository.save(new OrderLineItem(ticket, item, e.getValue(), item.getPrice())));
        }
        lineRepository.flush();

        return toDetail(ticket, saved);
    }

    public List<OrderResponse> list(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleException("'to' date must not be before 'from' date");
        }
        List<OrderTicket> tickets;
        if (from == null && to == null) {
            tickets = ticketRepository.findAllNewestFirst();
        } else {
            LocalDateTime start = (from != null ? from : LocalDate.of(1900, 1, 1)).atStartOfDay();
            LocalDateTime end = (to != null ? to : LocalDate.of(9999, 1, 1)).plusDays(1).atStartOfDay();
            tickets = ticketRepository.findInRangeNewestFirst(start, end);
        }
        if (tickets.isEmpty()) {
            return List.of();
        }
        // one batch query for all lines, then derive totals
        List<Integer> ids = tickets.stream().map(OrderTicket::getOrderId).toList();
        Map<Integer, BigDecimal> totals = new HashMap<>();
        for (OrderLineItem l : lineRepository.findByOrderIds(ids)) {
            totals.merge(l.getId().getOrderId(), l.getSubtotal(), BigDecimal::add);
        }
        return tickets.stream().map(t -> new OrderResponse(t.getOrderId(), t.getOrderTimestamp(),
                t.getStaff().getStaffId(), t.getStaff().getFullName(), t.getPaymentMethod(),
                totals.getOrDefault(t.getOrderId(), BigDecimal.ZERO.setScale(2)))).toList();
    }

    public OrderDetailResponse get(Integer id) {
        OrderTicket ticket = ticketRepository.findByIdWithStaff(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " not found"));
        return toDetail(ticket, lineRepository.findByOrderId(id));
    }

    /** Total = sum(quantity x sold_price); same formula for details, history and reports. */
    private static OrderDetailResponse toDetail(OrderTicket t, List<OrderLineItem> lines) {
        List<OrderLineResponse> lineDtos = lines.stream()
                .sorted(Comparator.comparing(l -> l.getId().getItemId()))
                .map(l -> new OrderLineResponse(l.getItem().getItemId(), l.getItem().getItemName(),
                        l.getQuantity(), l.getSoldPrice(), l.getSubtotal()))
                .toList();
        BigDecimal total = lineDtos.stream().map(OrderLineResponse::subtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new OrderDetailResponse(t.getOrderId(), t.getOrderTimestamp(), t.getStaff().getStaffId(),
                t.getStaff().getFullName(), t.getPaymentMethod(), lineDtos, total);
    }
}
