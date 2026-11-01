package com.brewpos.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.brewpos.dto.StaffResponse;
import com.brewpos.service.StaffService;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffResponse> list(@RequestParam(required = false) String role) {
        return staffService.list(role);
    }

    @GetMapping("/{id}")
    public StaffResponse get(@PathVariable Integer id) {
        return staffService.get(id);
    }
}
