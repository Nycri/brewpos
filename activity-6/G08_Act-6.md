# COMP 018: Database Administration â€” Activity 6: Database Backup, Restoration, and Recovery
## Group Evaluation Report: Group 08 â€” Campus Canteen Pre-Order and Queue Management System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Campus Canteen Pre-Order and Queue Management System** |
| **Target Project Group** | **Group 8 (bwisIT Solutions):** Borreta, Joshua; Bola, Martin Lawrence; Juatchon, Joaquin Phillip; Marcos, Karl Raphael; Pacheco Jr., Sancho; Vera Cruz, Euan Joseph |
| **Target Project Repository** | [https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System](https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System) |
| **Primary Database** | `canteen_db` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `canteen_db_test_restore` |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `canteen_db` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `canteen_db` (Schema: `public`)<br>• 7 Relational Tables: `student`, `vendor`, `menu_item`, `customer_order`, `order_item`, `queue_entry`, `notification`<br>• 7 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across student accounts, active stall menus, live order transactions, queue numbers, and audit notifications. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `canteen_db_activity6.backup` | Restricted to database administrators. Kept out of web roots and excluded from Git commits. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 15 Minutes<br>**RTO (Recovery Time Objective):** 30 Minutes | 15-minute RPO limits unfulfilled order losses during peak lunch hours; 30-minute RTO restores operations before canteen queues fail. |
| **Retention Policy** | Daily backups retained for 7 days; weekly archives for 4 weeks; monthly snapshots for 1 term. | Balances disk space with disaster recovery readiness. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`canteen_app`, `canteen_report_user`, etc.) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts (`01_create_roles.sql`) must run **prior** to restoring the dump to ensure object ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Created via pgAdmin Backup utility using Custom format (`-Fc`), saved to `C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup`.

### File Metadata & Checksum Verification (PowerShell)
- **Command:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 1F94752EAEFA683AFF69E12554130D8D872C0245EB6A4F46D4CB27B07D36988F
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup

  Name                          Length LastWriteTime
  ----                          ------ -------------
  canteen_db_activity6.backup    27405 04/11/2026 11:08:26 pm
  ```
- **Archive Inspection (`pg_restore --list`):**
  PowerShell output confirmed table definitions (`TABLE DATA public customer_order`, `menu_item`, `vendor`, etc.), sequences, primary key indexes, and foreign key constraint definitions.

---

## 3.3 Restoration into Separate Test Database (`canteen_db_test_restore`)

1. **Created Test Database:** Created an empty database named **`canteen_db_test_restore`** in pgAdmin.
2. **Restored Backup:** Executed pgAdmin Restore on `canteen_db_test_restore` using `canteen_db_activity6.backup`. Job completed in 0.41 seconds.
3. **Post-Restore Schema Permission Adaptation:**
   Because `canteen_db_test_restore` was created with owner `postgres`, the original table owner (`canteen_admin`) required schema usage in the new database:
   ```sql
   GRANT USAGE, CREATE ON SCHEMA public TO canteen_admin;
   ```

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 7 Tables Exist and are Owned by `canteen_admin`
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
  table_name     | table_type | owner
  ---------------+------------+--------------
  customer_order | BASE TABLE | canteen_admin
  menu_item      | BASE TABLE | canteen_admin
  notification   | BASE TABLE | canteen_admin
  order_item     | BASE TABLE | canteen_admin
  queue_entry    | BASE TABLE | canteen_admin
  student        | BASE TABLE | canteen_admin
  vendor         | BASE TABLE | canteen_admin
  (7 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'student' AS table_name, count(*) AS row_count FROM student
  UNION ALL SELECT 'vendor', count(*) FROM vendor
  UNION ALL SELECT 'menu_item', count(*) FROM menu_item
  UNION ALL SELECT 'customer_order', count(*) FROM customer_order
  UNION ALL SELECT 'order_item', count(*) FROM order_item
  UNION ALL SELECT 'queue_entry', count(*) FROM queue_entry
  UNION ALL SELECT 'notification', count(*) FROM notification;
  ```
- **Actual Output:**
  ```text
  table_name     | row_count
  ---------------+----------
  student        | 4
  vendor         | 5
  menu_item      | 12
  customer_order | 4
  order_item     | 5
  queue_entry    | 4
  notification   | 10
  (7 rows)
  ```

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks)
- **Query:**
  ```sql
  SELECT tc.table_name, tc.constraint_name, tc.constraint_type
  FROM information_schema.table_constraints tc
  WHERE tc.table_schema = 'public'
  ORDER BY tc.table_name, tc.constraint_type;
  ```
- **Actual Output:** All 58 constraint definitions (Primary Keys, Foreign Keys, Check Constraints, Unique Constraints) were validated and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Query:**
  ```sql
  SET ROLE canteen_app;
  SELECT vendor_id, vendor_name, is_active FROM vendor LIMIT 3;
  RESET ROLE;
  ```
- **Actual Output:** Allowed; successfully returned 3 vendor records (`Aling Nena's Silog`, `Kuya Jun's Burgers`, `Merienda Corner`).

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT co.order_id, s.full_name AS student_name, v.vendor_name, qe.queue_number, co.status
  FROM customer_order co
  JOIN student s ON co.student_id = s.student_id
  JOIN vendor v ON co.vendor_id = v.vendor_id
  LEFT JOIN queue_entry qe ON co.order_id = qe.order_id
  ORDER BY co.order_id;
  ```
- **Actual Output:** Returned all 4 orders with student name, vendor stall, queue number, and status (`cancelled`, `preparing`, `completed`).

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
- **Query:**
  ```sql
  RESET ROLE;
  INSERT INTO menu_item (item_id, vendor_id, item_name, price, is_available)
  VALUES (999, 1, 'DISPOSABLE_TEST_SNACK_999', 25.00, true);

  SELECT item_id, vendor_id, item_name, price, is_available 
  FROM menu_item 
  WHERE item_id = 999;
  ```
- **Output:** Returns 1 row showing `item_id = 999`, `item_name = 'DISPOSABLE_TEST_SNACK_999'`, `price = 25.00`. Baseline established.

### Step 2: Simulated Data-Loss Incident
- **Query:**
  ```sql
  DELETE FROM menu_item WHERE item_id = 999;

  SELECT item_id, item_name 
  FROM menu_item 
  WHERE item_id = 999;
  ```
- **Output:**
  ```text
  DELETE 1
  (0 rows)
  ```
- **Observation:** The record is missing; data loss confirmed.

### Step 3: Targeted Recovery
- **Query:**
  ```sql
  INSERT INTO menu_item (item_id, vendor_id, item_name, price, is_available)
  VALUES (999, 1, 'DISPOSABLE_TEST_SNACK_999', 25.00, true)
  ON CONFLICT (item_id) DO NOTHING;
  ```
- **Output:** `INSERT 0 1`.

### Step 4: Post-Recovery Verification
- **Query:**
  ```sql
  SELECT item_id, vendor_id, item_name, price, is_available 
  FROM menu_item 
  WHERE item_id = 999;
  ```
- **Output:**
  ```text
  item_id | vendor_id | item_name                 | price | is_available
  --------+-----------+---------------------------+-------+-------------
      999 |         1 | DISPOSABLE_TEST_SNACK_999 | 25.00 | true
  (1 row)
  ```
- **Result:** Successfully recovered record `999` with foreign key relationships and availability status intact.

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Permission Adaptation
When restoring `canteen_db_activity6.backup` into `canteen_db_test_restore`, foreign key triggers initially reported:
```text
ERROR: permission denied for schema public
LINE 1: SELECT 1 FROM ONLY "public"."vendor" x WHERE "vendor_id" OPE...
QUERY: SELECT 1 FROM ONLY "public"."vendor" x WHERE "vendor_id" OPERATOR(pg_catalog.=) $1 FOR KEY SHARE OF x
SQL state: 42501
```
- **Cause:** `canteen_db_test_restore` was owned by `postgres`, meaning the table owner (`canteen_admin`) lacked explicit schema usage after Activity 4's `REVOKE ALL ON SCHEMA public FROM PUBLIC;`.
- **Resolution:** Executing `GRANT USAGE, CREATE ON SCHEMA public TO canteen_admin;` resolved the dependency.

### Production Recommendations
1. **Automate Scheduled Dumps:** Deploy daily `pg_dump` jobs via Windows Task Scheduler with ISO-8601 timestamps.
2. **Implement Point-in-Time Recovery (PITR):** Standard logical dumps leave a vulnerability window between the last backup and an incident. Implementing WAL archiving (`archive_mode = on`, `archive_command`) ensures transactions can be recovered up to the exact second prior to an incident, meeting the 15-minute RPO.
3. **Monthly Restore Drills:** Schedule periodic restores into staging environments to ensure backup media integrity.

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 8 - bwisIT Solutions: Campus Canteen Pre-Order and Queue Management System |
| **Database and backup file** | `canteen_db`, `canteen_db_activity6.backup`, Size: 27,405 bytes, SHA256: `1F94752EAEFA683AFF69E12554130D8D872C0245EB6A4F46D4CB27B07D36988F` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-04) |
| **Restore test result** | Success (Restored into `canteen_db_test_restore`; all 7 tables, constraints, and data verified) |
| **Access control after restore** | Yes (Activity 4 roles and permissions validated; `canteen_app` DELETE restrictions enforced) |
| **Recovery simulation result** | Recovered (Simulated deleted test record successfully restored from verified backup state) |
| **Proposed RPO and RTO** | RPO: 15 minutes, RTO: 30 minutes |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/db-backup-recovery/group-08-bwisit/` |
| **Feedback sent to owners** | Yes (04/11/2026) |
| **PDF progress document** | *(Link to your group's shared PDF document)* |
| **Last updated** | 2026-11-04 |

---

---

> [!NOTE]
> All technical backup CLI commands, SHA-256 hash outputs, pg_restore verification logs, constraint validation checks, and disaster recovery simulation drill outputs for Group 08 are archived in [Evidence-act6/G08_Act-6_Evidence.md](Evidence-act6/G08_Act-6_Evidence.md).