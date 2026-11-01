package com.brewpos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesResponse(LocalDate date, BigDecimal totalSales, long orderCount) {
}
