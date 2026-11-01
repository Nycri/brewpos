package com.brewpos.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.brewpos.dto.DailySalesResponse;
import com.brewpos.dto.PaymentMethodSalesResponse;
import com.brewpos.service.SalesReportService;

@RestController
@RequestMapping("/api/reports")
public class SalesReportController {

    private final SalesReportService reportService;

    public SalesReportController(SalesReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily-sales")
    public DailySalesResponse dailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.dailySales(date);
    }

    @GetMapping("/daily-sales/by-payment")
    public List<PaymentMethodSalesResponse> byPayment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.salesByPayment(date);
    }
}
