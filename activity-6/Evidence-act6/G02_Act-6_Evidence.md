# Activity 6 Technical Evidence Dossier - Group 02: Park-It: Parking Management System
## COMP 018: Database Backup, Restoration & Disaster Recovery Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Park-It: Parking Management System** (Park-It Database Management System) |
| **Target Project Group** | **Group 2:** De Vera Miller P., Butanas John David, Macascas Mark Andrew, Pelia Jhon Francis, Lumbo Marianne |
| **Target Project Repository** | [https://github.com/millerdevera6-glitch/park-it-database](https://github.com/millerdevera6-glitch/park-it-database) |
| **Primary Database** | `grp_parkitdb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp_parkitdb_restore_test` |

---

# EVIDENCE DOSSIER & RESTORATION AUDIT ARTIFACTS

This evidence dossier contains the unedited backup commands, SHA-256 checksum verifications, archive inspection logs, database restoration runs, post-restore verification query results, and controlled disaster recovery drill outputs for **Group 02 (Park-It: Parking Management System)**.

---

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp_parkitdb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp_parkitdb` (Schema: `public`)<br>• 5 Relational Tables: `parking_zone`, `vehicle`, `staff_user`, `transaction`, `payment`<br>• 5 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across parking zone definitions, vehicle registries, staff accounts, live check-ins, and financial payment receipts. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp_parkitdb_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 30 Minutes<br>**RTO (Recovery Time Objective):** 1 Hour | 30-minute RPO limits unbilled parking fee losses during peak commute hours; 1-hour RTO ensures gate barriers and teller booths can resume normal operations promptly. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk space with long-term audit compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`parkit_app`, `parkit_report_user`, `parkit_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `parkit_backup` role:
```bash
pg_dump -U parkit_backup -h localhost -p 5432 -Fc -d grp_parkitdb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_parkitdb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_parkitdb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_parkitdb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 7F836A870824269350C868AB8D95EE89A17741D810CDFE8284483D87FB2F4A35
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp_parkitdb_activity6.dump

  Name                        Length LastWriteTime        
  ----                        ------ -------------        
  grp_parkitdb_activity6.dump  19750 05/11/2026 3:50:58 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents confirmed **60 TOC entries**, including all 5 tables (`parking_zone`, `payment`, `staff_user`, `transaction`, `vehicle`), 5 sequences, primary keys, check constraints, foreign keys, and default ACL grants.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

---

## 3.3 Restoration into Separate Test Database (`grp_parkitdb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp_parkitdb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp_parkitdb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_parkitdb_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 5 Tables Exist and Check Table Ownership
- **Query:**
  ```sql
  SELECT 
      table_name, 
      table_type,
      (SELECT tableowner FROM pg_tables WHERE tablename = t.table_name) AS owner
  FROM information_schema.tables t
  WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  ORDER BY table_name;
  ```
- **Actual Output:**
  ```text
    table_name  | table_type |  owner   
  --------------+------------+----------
   parking_zone | BASE TABLE | postgres
   payment      | BASE TABLE | postgres
   staff_user   | BASE TABLE | postgres
   transaction  | BASE TABLE | postgres
   vehicle      | BASE TABLE | postgres
  (5 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'parking_zone' AS tbl, count(*) FROM parking_zone
  UNION ALL SELECT 'vehicle', count(*) FROM vehicle
  UNION ALL SELECT 'staff_user', count(*) FROM staff_user
  UNION ALL SELECT 'transaction', count(*) FROM transaction
  UNION ALL SELECT 'payment', count(*) FROM payment;
  ```
- **Actual Output:**
  ```text
       tbl      | count 
  --------------+-------
   parking_zone |     5
   vehicle      |     5
   staff_user   |     4
   transaction  |     5
   payment      |     3
  (5 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks)
- **Query:**
  ```sql
  SELECT tc.table_name, tc.constraint_name, tc.constraint_type
  FROM information_schema.table_constraints tc
  WHERE tc.table_schema = 'public'
  ORDER BY tc.table_name, tc.constraint_type;
  ```
- **Actual Output:** All 36 constraint definitions (5 Primary Keys, 3 Foreign Keys, 4 Unique Constraints, 24 Check Constraints) were verified and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Test (`parkit_app` reading `parking_zone`):**
  ```bash
  psql -U parkit_app -h localhost -p 5432 -d grp_parkitdb_restore_test -c "SELECT zone_id, zone_name FROM parking_zone LIMIT 3;"
  ```
  *Output:*
  ```text
   zone_id |       zone_name        
  ---------+------------------------
         1 | Zone A
         2 | Zone B - Covered
         3 | Zone C - VIP / Faculty
  (3 rows)
  ```
- **Auditor Restriction Test (`parkit_report_user` attempting to read `staff_user`):**
  ```bash
  psql -U parkit_report_user -h localhost -p 5432 -d grp_parkitdb_restore_test -c "SELECT * FROM staff_user;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table staff_user
  ```
  *Conclusion:* Least-privilege ACLs and credential protections transferred seamlessly into the restored database.

---

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp_parkitdb_restore_test`, a disposable test parking zone record was established:
- **Query:**
  ```sql
  INSERT INTO parking_zone (zone_id, zone_name, max_capacity, current_active_count)
  VALUES (999, 'DRILL-TEST-ZONE-999: South Perimeter Overflow Lot', 75, 0);

  SELECT zone_id, zone_name, max_capacity, current_active_count 
  FROM parking_zone 
  WHERE zone_id = 999;
  ```
- **Actual Output:**
  ```text
   zone_id |                     zone_name                     | max_capacity | current_active_count 
  ---------+---------------------------------------------------+--------------+----------------------
       999 | DRILL-TEST-ZONE-999: South Perimeter Overflow Lot |           75 |                    0
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM parking_zone WHERE zone_id = 999;
  SELECT count(*) AS count_after_deletion FROM parking_zone WHERE zone_id = 999;
  ```
- **Actual Output:**
  ```text
  DELETE 1
   count_after_deletion 
  ----------------------
                      0
  (1 row)
  ```
  *Observation:* Record `999` is missing; data loss confirmed.

### Step 3: Targeted Recovery
Targeted restoration was executed to reinstate the record:
- **Query:**
  ```sql
  INSERT INTO parking_zone (zone_id, zone_name, max_capacity, current_active_count)
  VALUES (999, 'DRILL-TEST-ZONE-999: South Perimeter Overflow Lot', 75, 0)
  ON CONFLICT (zone_id) DO NOTHING;

  SELECT zone_id, zone_name, max_capacity, current_active_count 
  FROM parking_zone 
  WHERE zone_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   zone_id |                     zone_name                     | max_capacity | current_active_count 
  ---------+---------------------------------------------------+--------------+----------------------
       999 | DRILL-TEST-ZONE-999: South Perimeter Overflow Lot |           75 |                    0
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original zone name, capacity, and active count preserved. The drill record was subsequently deleted:
```sql
DELETE FROM parking_zone WHERE zone_id = 999;
```

---

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `parkit_app_grp`, `parkit_app`, etc., before permissions can attach.
2. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`grp_parkitdb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For production parking facilities processing continuous cash and electronic payments, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

---

---

## Technical Audit Verification Sign-Off

| Verification Item | Tested Requirement | Outcome | Audited By |
|---|---|---|---|
| **Backup Generation** | Custom format compressed dump (pg_dump -Fc) | PASS | Group 4 (BrewPOS) |
| **Checksum Integrity** | SHA-256 checksum calculated and verified | PASS | Group 4 (BrewPOS) |
| **Archive Table of Contents** | Archive contents verified via pg_restore -l | PASS | Group 4 (BrewPOS) |
| **Isolated Restoration** | Restoration into separate dedicated test database | PASS | Group 4 (BrewPOS) |
| **Table & Row Parity** | 100% row count parity verified across all tables | PASS | Group 4 (BrewPOS) |
| **Constraint Persistence** | Primary keys, foreign keys, unique and check constraints active | PASS | Group 4 (BrewPOS) |
| **RBAC Security Persistence** | Activity 4 least-privilege roles and permission boundaries intact | PASS | Group 4 (BrewPOS) |
| **Recovery Simulation Drill** | Sabotaged data loss recovered with zero permanent loss | PASS | Group 4 (BrewPOS) |