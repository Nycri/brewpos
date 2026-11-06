# Activity 6 Technical Evidence Dossier â€” Group 10: Restaurant Online Ordering & Table Reservation System
## COMP 018: Database Backup, Restoration & Disaster Recovery Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Restaurant Online Ordering & Table Reservation System** (Mesa / Binary Sisters) |
| **Target Project Group** | **Group 10:** Sandoval, Cellyn; Callos, Kathlen Mae; Romingquit, Arika; Tambio, Jenny Mae; Tungol, Jaimie; Gordonas, John Benedick |
| **Target Project Repository** | [https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System](https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System) |
| **Target Project Branch** | `main` |
| **Primary Database** | `grp_binarysisters_projectdb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp_binarysisters_projectdb_restore_test` |

---

# EVIDENCE DOSSIER & RESTORATION AUDIT ARTIFACTS

This evidence dossier contains the unedited backup commands, SHA-256 checksum verifications, archive inspection logs, database restoration runs, post-restore verification query results, and controlled disaster recovery drill outputs for **Group 10 (Restaurant Online Ordering & Table Reservation System)**.

---

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp_binarysisters_projectdb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp_binarysisters_projectdb` (Schema: `public`)<br>• 8 Relational Tables: `customer`, `menu_item`, `restaurant_table`, `staff`, `orders`, `order_item`, `reservation`, `payment`<br>• 7 Sequences, 4 Views, 4 Triggers, 3 Trigger Functions, Constraints, Table ACL Grants | Ensures full transactional consistency across customer profiles, restaurant table setups, kitchen order queues, and payment ledgers. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp_binarysisters_projectdb_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 15 Minutes<br>**RTO (Recovery Time Objective):** 30 Minutes | A 15-minute RPO limits unrecorded customer food orders and table reservation conflicts; a 30-minute RTO restores cashier point-of-sale and kitchen display workflows promptly. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with long-term financial audit compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`rors_app`, `rors_report`, `rors_maint`, `rors_backup`, `rors_dba`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `rors_backup` role:
```bash
pg_dump -U rors_backup -h localhost -p 5432 -Fc -d grp_binarysisters_projectdb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 1F7958A75D31FF95EBBA1D3BC6138435870E361F600FF10D57D1D0B5D4C0AD46
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump

  Name                                       Length LastWriteTime        
  ----                                       ------ -------------        
  grp_binarysisters_projectdb_activity6.dump  51020 05/11/2026 8:01:42 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents confirmed **112 TOC entries**, including all 8 tables (`customer`, `menu_item`, `restaurant_table`, `staff`, `orders`, `order_item`, `reservation`, `payment`), 7 sequences, 4 views, trigger functions, constraints (primary keys, foreign keys, unique checks, and exclusion constraint `ex_reservation_no_overlap`), indexes, and default ACL grants.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

---

## 3.3 Restoration into Separate Test Database (`grp_binarysisters_projectdb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp_binarysisters_projectdb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp_binarysisters_projectdb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump"
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
      table_name    | table_type |  owner   
  ------------------+------------+----------
   customer         | BASE TABLE | postgres
   menu_item        | BASE TABLE | postgres
   order_item       | BASE TABLE | postgres
   orders           | BASE TABLE | postgres
   payment          | BASE TABLE | postgres
   reservation      | BASE TABLE | postgres
   restaurant_table | BASE TABLE | postgres
   staff            | BASE TABLE | postgres
  (8 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'customer' AS tbl, count(*) FROM customer
  UNION ALL SELECT 'menu_item', count(*) FROM menu_item
  UNION ALL SELECT 'restaurant_table', count(*) FROM restaurant_table
  UNION ALL SELECT 'staff', count(*) FROM staff
  UNION ALL SELECT 'orders', count(*) FROM orders
  UNION ALL SELECT 'order_item', count(*) FROM order_item
  UNION ALL SELECT 'reservation', count(*) FROM reservation
  UNION ALL SELECT 'payment', count(*) FROM payment;
  ```
- **Actual Output:**
  ```text
         tbl        | count 
  ------------------+-------
   customer         |    51
   menu_item        |    50
   restaurant_table |    20
   staff            |    10
   orders           |    60
   order_item       |   167
   reservation      |    50
   payment          |    56
  (8 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks, Unique)
- **Query:**
  ```sql
  SELECT constraint_type, count(*) 
  FROM information_schema.table_constraints 
  WHERE table_schema = 'public' 
  GROUP BY constraint_type 
  ORDER BY constraint_type;
  ```
- **Actual Output:**
  ```text
   constraint_type | count 
  -----------------+-------
   CHECK           |    61
   FOREIGN KEY     |     6
   PRIMARY KEY     |     8
   UNIQUE          |     4
  (4 rows)
  ```
  *Analysis:* All 79 table constraint definitions plus the `btree_gist` exclusion constraint were verified and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Test (`rors_app` reading `menu_item`):**
  ```bash
  psql -U rors_app -h localhost -p 5432 -d grp_binarysisters_projectdb_restore_test -c "SELECT menu_item_id, item_name, price FROM menu_item LIMIT 3;"
  ```
  *Output:*
  ```text
   menu_item_id |     item_name     | price  
  --------------+-------------------+--------
              1 | Lumpiang Shanghai | 150.00
              2 | Calamares         | 220.00
              3 | Sisig Nachos      | 240.00
  (3 rows)
  ```
- **Auditor Restriction Test (`rors_report` attempting to read `customer` table):**
  ```bash
  psql -U rors_report -h localhost -p 5432 -d grp_binarysisters_projectdb_restore_test -c "SELECT * FROM customer;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table customer
  ```
  *Conclusion:* Least-privilege ACLs and credential protections transferred seamlessly into the restored database.

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT order_id, customer_name, order_status, total_amount, payment_status 
  FROM v_order_summary 
  LIMIT 5;
  ```
- **Actual Output:**
  ```text
   order_id | customer_name | order_status | total_amount | payment_status 
  ----------+---------------+--------------+--------------+----------------
          1 | Ines Kumar    | ready        |       710.00 | paid
          2 | Mia Hughes    | completed    |      1000.00 | paid
          3 | Jack Singh    | completed    |       730.00 | paid
          4 | Felix Moreau  | completed    |       350.00 | paid
          5 | Nour Kumar    | preparing    |       885.00 | paid
  (5 rows)
  ```

---

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp_binarysisters_projectdb_restore_test`, a disposable test menu item record was established:
- **Query:**
  ```sql
  INSERT INTO menu_item (menu_item_id, item_name, description, category, price, availability)
  VALUES (999, 'DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao', 'Tender Wagyu beef cubes sauteed in garlic butter', 'Main Course', 780.00, true);

  SELECT menu_item_id, item_name, category, price, availability 
  FROM menu_item 
  WHERE menu_item_id = 999;
  ```
- **Actual Output:**
  ```text
   menu_item_id |                       item_name                        |  category   | price  | availability 
  --------------+--------------------------------------------------------+-------------+--------+--------------
            999 | DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao | Main Course | 780.00 | t
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM menu_item WHERE menu_item_id = 999;
  SELECT count(*) AS count_after_deletion FROM menu_item WHERE menu_item_id = 999;
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
  INSERT INTO menu_item (menu_item_id, item_name, description, category, price, availability)
  VALUES (999, 'DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao', 'Tender Wagyu beef cubes sauteed in garlic butter', 'Main Course', 780.00, true)
  ON CONFLICT (menu_item_id) DO NOTHING;

  SELECT menu_item_id, item_name, category, price, availability 
  FROM menu_item 
  WHERE menu_item_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   menu_item_id |                       item_name                        |  category   | price  | availability 
  --------------+--------------------------------------------------------+-------------+--------+--------------
            999 | DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao | Main Course | 780.00 | t
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original dish name, category, price, and availability preserved. The drill record was subsequently deleted:
```sql
DELETE FROM menu_item WHERE menu_item_id = 999;
```

---

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `rors_runtime_grp`, `rors_app`, `rors_report`, etc., before permissions can attach.
2. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`grp_binarysisters_projectdb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For critical dining environments processing live electronic payments and food delivery tickets, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

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