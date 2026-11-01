package com.brewpos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewpos.entity.Staff;

public interface StaffRepository extends JpaRepository<Staff, Integer> {

    List<Staff> findByRoleOrderByFullName(String role);

    List<Staff> findAllByOrderByFullName();
}
