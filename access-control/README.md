# COMP 018 — Activity 4: Database Access Control Directory

This directory contains the complete Activity 4 deliverables, reports, and security audit verifications conducted by **Group 4 (BrewPOS)** on peer databases for the **COMP 018: Database Administration** course.

## Overview
Activity 4 establishes strict **Role-Based Access Control (RBAC)** and enforces the **Principle of Least Privilege (PoLP)**:
1. Eliminating application runtime connections as table-owner/superuser (`postgres`).
2. Splitting responsibilities into unprivileged Group Roles (`NOLOGIN`) and dedicated Login Roles (`LOGIN`).
3. Enforcing narrow DML (`SELECT`, `INSERT`, `UPDATE`, selective `DELETE`).
4. Protecting sensitive staff/user credential tables from auditor and unprivileged user queries.
5. Strictly denying all Data Definition Language (DDL) and cross-database access attempts.

## Deliverables in this Folder

| File Name | Evaluated Group | Project Monitored | Primary Database | Status |
|---|---|---|---|:---:|
| [G02_Act-4.md](G02_Act-4.md) | Group 02 | Park-It: Parking Management System | `grp_parkitdb` | Verified |
| [G03_Act-4.md](G03_Act-4.md) | Group 03 | OJT Management System | `ojt_management` | Verified |
| [G05_Act-4.md](G05_Act-4.md) | Group 05 | PUP-SPOT: Campus Lost and Found System | `grp05_lostandfounddb` | Verified |
| [G06_Act-4.md](G06_Act-4.md) | Group 06 | HabalGo: Ride and Booking Management System | `grp00_habalgodb` | Verified |
| [G07_Act-4.md](G07_Act-4.md) | Group 07 | CampusSafe: Campus Hazard & Safety Reporting System | `grp07_campussafedb` | Verified |
| [G08_Act-4.md](G08_Act-4.md) | Group 08 | Campus Canteen Pre-Order and Queue Management System | `canteen_db` | Verified |
| [G10_Act-4.md](G10_Act-4.md) | Group 10 | Restaurant Online Ordering & Table Reservation System | `grp_binarysisters_projectdb` | Verified |
| [G11_Act-4.md](G11_Act-4.md) | Group 11 | BoardTrack: Boarding House & Rental Management System | `boardtrack_db` | Verified |
