# COMP 018 — Activity 5: Database Maintenance and Monitoring Directory

This directory contains the Activity 5 deliverables, performance reports, and the **Evidence-act5** technical evidence subfolder for the peer databases evaluated by **Group 4 (BrewPOS)**.

## Overview
Activity 5 executes proactive database administration:
1. **Database Health & Sizing:** Monitoring storage footprints, heap sizes, and index sizes.
2. **Session & Activity Auditing:** Verifying zero transaction conflicts or deadlocks and healthy connection counts.
3. **Table Statistics & Bloat Inspection:** Identifying dead tuples resulting from DML testing.
4. **Routine Maintenance:** Executing `ANALYZE` and `VACUUM (VERBOSE, ANALYZE)` without locking tables (`VACUUM FULL` excluded).
5. **Execution Plan Profiling:** Auditing representative multi-table joins using `EXPLAIN (ANALYZE, BUFFERS)` to verify sub-millisecond execution and 100% cache hits.

## Deliverables in this Folder

| File Name | Evaluated Group | Project Monitored | Primary Database | Technical Evidence | Status |
|---|---|---|---|---|:---:|
| [G02_Act-5.md](G02_Act-5.md) | Group 02 | Park-It: Parking Management System | `grp_parkitdb` | [G02 Evidence](Evidence-act5/G02_Act-5_Evidence.md) | Verified |
| [G03_Act-5.md](G03_Act-5.md) | Group 03 | OJT Management System | `ojt_management` | [G03 Evidence](Evidence-act5/G03_Act-5_Evidence.md) | Verified |
| [G05_Act-5.md](G05_Act-5.md) | Group 05 | PUP-SPOT: Campus Lost and Found System | `grp05_lostandfounddb` | [G05 Evidence](Evidence-act5/G05_Act-5_Evidence.md) | Verified |
| [G06_Act-5.md](G06_Act-5.md) | Group 06 | HabalGo: Ride and Booking Management System | `grp00_habalgodb` | [G06 Evidence](Evidence-act5/G06_Act-5_Evidence.md) | Verified |
| [G07_Act-5.md](G07_Act-5.md) | Group 07 | CampusSafe: Campus Hazard & Safety Reporting System | `grp07_campussafedb` | [G07 Evidence](Evidence-act5/G07_Act-5_Evidence.md) | Verified |
| [G08_Act-5.md](G08_Act-5.md) | Group 08 | Campus Canteen Pre-Order and Queue Management System | `canteen_db` | [G08 Evidence](Evidence-act5/G08_Act-5_Evidence.md) | Verified |
| [G10_Act-5.md](G10_Act-5.md) | Group 10 | Restaurant Online Ordering & Table Reservation System | `grp_binarysisters_projectdb` | [G10 Evidence](Evidence-act5/G10_Act-5_Evidence.md) | Verified |
| [G11_Act-5.md](G11_Act-5.md) | Group 11 | BoardTrack: Boarding House & Rental Management System | `boardtrack_db` | [G11 Evidence](Evidence-act5/G11_Act-5_Evidence.md) | Verified |

- **[Evidence-act5/](Evidence-act5/)** — Subfolder containing all raw query scripts, full EXPLAIN logs, terminal traces, and execution evidence.
