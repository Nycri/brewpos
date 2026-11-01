package com.brewpos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewpos.dto.StaffResponse;
import com.brewpos.entity.Staff;
import com.brewpos.exception.BusinessRuleException;
import com.brewpos.exception.ResourceNotFoundException;
import com.brewpos.repository.StaffRepository;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public List<StaffResponse> list(String role) {
        List<Staff> staff;
        if (role == null || role.isBlank()) {
            staff = staffRepository.findAllByOrderByFullName();
        } else {
            String normalized = role.trim().toUpperCase();
            if (!normalized.equals("CASHIER") && !normalized.equals("MANAGER")) {
                throw new BusinessRuleException("Role must be CASHIER or MANAGER");
            }
            staff = staffRepository.findByRoleOrderByFullName(normalized);
        }
        return staff.stream().map(StaffService::toResponse).toList();
    }

    public StaffResponse get(Integer id) {
        return staffRepository.findById(id).map(StaffService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Staff " + id + " not found"));
    }

    private static StaffResponse toResponse(Staff s) {
        return new StaffResponse(s.getStaffId(), s.getFullName(), s.getRole());
    }
}
