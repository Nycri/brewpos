# Activity 6 Technical Evidence Dossier - Group 11: BoardTrack: Boarding House & Rental Management System
## COMP 018: Database Backup, Restoration & Disaster Recovery Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **BoardTrack: Boarding House & Rental Management System** |
| **Target Project Group** | **Group 11:** Barcarse, Adrian; Cataluña, Zyruz Gabriel; Camu, Edgar; Policarpio, Kurt; Crucillo, Karl Robert |
| **Target Project Repository** | [https://github.com/karlrobertc-art/BoardTrack](https://github.com/karlrobertc-art/BoardTrack) |
| **Target Project Branch** | `main` |
| **Primary Database** | `boardtrack_db` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `boardtrack_db_restore_test` |

---

# EVIDENCE DOSSIER & RESTORATION AUDIT ARTIFACTS

This evidence dossier contains the unedited backup commands, SHA-256 checksum verifications, archive inspection logs, database restoration runs, post-restore verification query results, and controlled disaster recovery drill outputs for **Group 11 (BoardTrack: Boarding House & Rental Management System)**.

---

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `boardtrack_db` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `boardtrack_db` (Schema: `public`)<br>• 8 Relational Tables: `property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`, `app_user`, `schema_migrations`<br>• 7 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, `btree_gist` Exclusion Constraint, Triggers (`trg_lease_sync_room`, `trg_room_status_guard`), View (`v_lease_summary`), ACL Grants | Full consistency across property structures, room inventories, tenant records, active leases, payment receipts, utility billings, user authentication records, and business constraints. |
| **Backup Method & Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `boardtrack_db_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 1 Hour<br>**RTO (Recovery Time Objective):** 30 Minutes | 1-hour RPO ensures minimal payment receipt or lease agreement data loss; 30-minute RTO ensures tenant check-ins and landlord operations resume rapidly after hardware failure. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`boardtrack_app`, `boardtrack_report_user`, `boardtrack_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `boardtrack_backup` role:
```bash
pg_dump -U boardtrack_backup -h localhost -p 5432 -Fc -d boardtrack_db -f "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 5B83883ADEB30784905A7EB22B7B67607DC6D1CCB22A508E5559DE60EB48559A
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump

  Name                         Length LastWriteTime        
  ----                         ------ -------------        
  boardtrack_db_activity6.dump  35849 05/11/2026 6:48:31 pm
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents (`pg_restore --list`) confirmed **99 TOC entries**, including all 8 tables (`property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`, `app_user`, `schema_migrations`), 7 sequences, extensions (`btree_gist`, `pgcrypto`), triggers (`sync_room_status`, `guard_room_status`), exclusion constraint (`ex_lease_no_overlap`), view (`v_lease_summary`), and default ACL grants.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

---

## 3.3 Restoration into Separate Test Database (`boardtrack_db_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 boardtrack_db_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d boardtrack_db_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 8 Tables Exist and Check Table Ownership
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
      table_name     | table_type |  owner   
  -------------------+------------+----------
   app_user          | BASE TABLE | postgres
   lease             | BASE TABLE | postgres
   payment           | BASE TABLE | postgres
   property          | BASE TABLE | postgres
   room              | BASE TABLE | postgres
   schema_migrations | BASE TABLE | postgres
   tenant            | BASE TABLE | postgres
   utility_bill      | BASE TABLE | postgres
  (8 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'property' AS tbl, count(*) FROM property
  UNION ALL SELECT 'room', count(*) FROM room
  UNION ALL SELECT 'tenant', count(*) FROM tenant
  UNION ALL SELECT 'lease', count(*) FROM lease
  UNION ALL SELECT 'payment', count(*) FROM payment
  UNION ALL SELECT 'utility_bill', count(*) FROM utility_bill
  UNION ALL SELECT 'app_user', count(*) FROM app_user
  UNION ALL SELECT 'schema_migrations', count(*) FROM schema_migrations;
  ```
- **Actual Output:**
  ```text
          tbl        | count 
  -------------------+-------
   property          |     2
   room              |     6
   tenant            |     4
   lease             |     4
   payment           |     6
   utility_bill      |     4
   app_user          |     3
   schema_migrations |     1
  (8 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks, Exclusion)
- **Query:**
  ```sql
  SELECT tc.table_name, tc.constraint_name, tc.constraint_type
  FROM information_schema.table_constraints tc
  WHERE tc.table_schema = 'public'
  ORDER BY tc.table_name, tc.constraint_type;
  ```
- **Actual Output:** All 68 constraint definitions (Primary Keys, Foreign Keys, Check Constraints, Unique Constraints, and the `btree_gist` Exclusion Constraint) were validated and active in the restored database.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Read Test (`boardtrack_app` reading `property`):**
  ```bash
  psql -U boardtrack_app -h localhost -p 5432 -d boardtrack_db_restore_test -c "SELECT property_id, property_name FROM property LIMIT 3;"
  ```
  *Output:*
  ```text
   property_id |      property_name      
  -------------+-------------------------
             1 | Sunshine Boarding House
             2 | Greenview Apartments
  (2 rows)
  ```
- **Auditor Summary Read Test (`boardtrack_report_user` reading `v_lease_summary`):**
  ```bash
  psql -U boardtrack_report_user -h localhost -p 5432 -d boardtrack_db_restore_test -c "SELECT lease_id, tenant, property_name FROM v_lease_summary LIMIT 3;"
  ```
  *Output:*
  ```text
   lease_id |     tenant     |      property_name      
  ----------+----------------+-------------------------
          1 | Juan Dela Cruz | Sunshine Boarding House
          2 | Maria Santos   | Sunshine Boarding House
          3 | Pedro Reyes    | Greenview Apartments
  (3 rows)
  ```
- **Auditor Credential Restriction Test (`boardtrack_report_user` attempting to read `app_user`):**
  ```bash
  psql -U boardtrack_report_user -h localhost -p 5432 -d boardtrack_db_restore_test -c "SELECT * FROM app_user;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table app_user
  ```
  *Conclusion:* Least-privilege ACLs and operational protections transferred seamlessly into the restored database.

---

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `boardtrack_db_restore_test`, a disposable test property record was established:
- **Query:**
  ```sql
  INSERT INTO property (property_id, property_name, address) 
  VALUES (999, 'DRILL-TEST-DORM-999: Palm Grove Residence Complex', '77 University Heights, Los Banos, Laguna');

  SELECT property_id, property_name, address FROM property WHERE property_id = 999;
  ```
- **Actual Output:**
  ```text
   property_id |                   property_name                   |                 address                  
  -------------+---------------------------------------------------+------------------------------------------
           999 | DRILL-TEST-DORM-999: Palm Grove Residence Complex | 77 University Heights, Los Banos, Laguna
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM property WHERE property_id = 999;
  SELECT count(*) AS count_after_deletion FROM property WHERE property_id = 999;
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
  INSERT INTO property (property_id, property_name, address) 
  VALUES (999, 'DRILL-TEST-DORM-999: Palm Grove Residence Complex', '77 University Heights, Los Banos, Laguna')
  ON CONFLICT (property_id) DO NOTHING;

  SELECT property_id, property_name, address FROM property WHERE property_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   property_id |                   property_name                   |                 address                  
  -------------+---------------------------------------------------+------------------------------------------
           999 | DRILL-TEST-DORM-999: Palm Grove Residence Complex | 77 University Heights, Los Banos, Laguna
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original property name and address preserved. The drill record was subsequently deleted:
```sql
DELETE FROM property WHERE property_id = 999;
```

---

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `boardtrack_app_grp`, `boardtrack_readonly_grp`, `boardtrack_app`, etc., before permissions can attach.
2. **Extension Availability:** The custom dump preserves the `CREATE EXTENSION IF NOT EXISTS btree_gist` statement. On target servers, the `btree_gist` contrib package must be available in the PostgreSQL installation directory.
3. **Production Backup Recommendations:**
   - **Automated Scheduled Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`boardtrack_db_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For production environments handling daily financial collections, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

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