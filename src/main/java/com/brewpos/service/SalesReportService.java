package com.brewpos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.DailySalesResponse;
import com.brewpos.dto.PaymentMethodSalesResponse;
import com.brewpos.exception.BusinessRuleException;
import com.brewpos.repository.OrderLineItemRepository;

/**
 * Reports are computed by PostgreSQL over a half-open range
 * [date 00:00, next date 00:00). Timestamps are local cafe time (TIMESTAMP without zone).
 */
@Service
@Transactional(readOnly = true)
public class SalesReportService {

    private final OrderLineItemRepository lineRepository;

    public SalesReportService(OrderLineItemRepository lineRepository) {
        this.lineRepository = lineRepository;
    }

    public DailySalesResponse dailySales(LocalDate date) {
        LocalDate d = requireDate(date);
        Object[] row = lineRepository.dailyTotals(start(d), end(d)).get(0);
        return new DailySalesResponse(d, money(row[0]), ((Number) row[1]).longValue());
    }

    /** Always returns both CASH and ONLINE, with zeros where there are no orders. */
    public List<PaymentMethodSalesResponse> salesByPayment(LocalDate date) {
        LocalDate d = requireDate(date);
        List<Object[]> rows = lineRepository.totalsByPaymentMethod(start(d), end(d));
        List<PaymentMethodSalesResponse> result = new ArrayList<>();
        for (String method : List.of("CASH", "ONLINE")) {
            BigDecimal total = BigDecimal.ZERO.setScale(2);
            long count = 0;
            for (Object[] r : rows) {
                if (method.equals(r[0])) {
                    total = money(r[1]);
                    count = ((Number) r[2]).longValue();
                }
            }
            result.add(new PaymentMethodSalesResponse(method, total, count));
        }
        return result;
    }

    private static LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleException("Date is required (format YYYY-MM-DD)");
        }
        return date;
    }

    private static LocalDateTime start(LocalDate d) {
        return d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return d.plusDays(1).atStartOfDay();
    }

    private static BigDecimal money(Object value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
