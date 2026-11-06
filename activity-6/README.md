# COMP 018 — Activity 6: Database Backup, Restoration, and Recovery Directory

This directory contains the Activity 6 deliverables, disaster recovery reports, and the **Evidence-act6** technical evidence subfolder for the peer databases evaluated by **Group 4 (BrewPOS)**.

## Overview
Activity 6 implements end-to-end database resilience and disaster recovery:
1. **Backup Architecture:** Creation of custom compressed binary archives via `pg_dump -Fc`.
2. **Cryptographic Checksum Verification:** Validation of archive integrity using SHA-256 hashes.
3. **Isolated Restoration:** Full restore into separate test databases (`*_restore_test`).
4. **Post-Restore Verification Suite:** Strict validation of table schemas, 100% row count parity, primary/foreign key constraints, and Activity 4 RBAC privilege preservation.
5. **Controlled Disaster Recovery Drill:** Intentional sabotage simulation (simulated data loss), targeted restore, and proof of 0% permanent data loss.

## Deliverables in this Folder

| File Name | Evaluated Group | Project Monitored | Primary Database | Technical Evidence | Status |
|---|---|---|---|---|:---:|
| [G02_Act-6.md](G02_Act-6.md) | Group 02 | Park-It: Parking Management System | `grp_parkitdb` | [G02 Evidence](Evidence-act6/G02_Act-6_Evidence.md) | Verified |
| [G03_Act-6.md](G03_Act-6.md) | Group 03 | OJT Management System | `ojt_management` | [G03 Evidence](Evidence-act6/G03_Act-6_Evidence.md) | Verified |
| [G05_Act-6.md](G05_Act-6.md) | Group 05 | PUP-SPOT: Campus Lost and Found System | `grp05_lostandfounddb` | [G05 Evidence](Evidence-act6/G05_Act-6_Evidence.md) | Verified |
| [G06_Act-6.md](G06_Act-6.md) | Group 06 | HabalGo: Ride and Booking Management System | `grp00_habalgodb` | [G06 Evidence](Evidence-act6/G06_Act-6_Evidence.md) | Verified |
| [G07_Act-6.md](G07_Act-6.md) | Group 07 | CampusSafe: Campus Hazard & Safety Reporting System | `grp07_campussafedb` | [G07 Evidence](Evidence-act6/G07_Act-6_Evidence.md) | Verified |
| [G08_Act-6.md](G08_Act-6.md) | Group 08 | Campus Canteen Pre-Order and Queue Management System | `canteen_db` | [G08 Evidence](Evidence-act6/G08_Act-6_Evidence.md) | Verified |
| [G10_Act-6.md](G10_Act-6.md) | Group 10 | Restaurant Online Ordering & Table Reservation System | `grp_binarysisters_projectdb` | [G10 Evidence](Evidence-act6/G10_Act-6_Evidence.md) | Verified |
| [G11_Act-6.md](G11_Act-6.md) | Group 11 | BoardTrack: Boarding House & Rental Management System | `boardtrack_db` | [G11 Evidence](Evidence-act6/G11_Act-6_Evidence.md) | Verified |

- **[Evidence-act6/](Evidence-act6/)** — Subfolder containing raw SHA-256 outputs, `pg_restore -l` archives, post-restore query proofs, and drill transcripts.
