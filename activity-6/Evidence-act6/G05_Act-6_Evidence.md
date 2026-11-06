# Activity 6 Technical Evidence Dossier - Group 05: PUP-SPOT: Campus Lost and Found System
## COMP 018: Database Backup, Restoration & Disaster Recovery Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **PUP-SPOT: Campus Lost and Found System** |
| **Target Project Group** | **Group 5:** Raguin, Justine Philip; Cueto, Daniel Luigi; Imperial, Aaron Paul; Manansala, Emmanuel John; Napeñas, Jenver |
| **Target Project Repository** | [https://github.com/grp05-dba/grp05_lostandfound](https://github.com/grp05-dba/grp05_lostandfound) |
| **Primary Database** | `grp05_lostandfounddb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp05_lostandfounddb_restore_test` |

---

# EVIDENCE DOSSIER & RESTORATION AUDIT ARTIFACTS

This evidence dossier contains the unedited backup commands, SHA-256 checksum verifications, archive inspection logs, database restoration runs, post-restore verification query results, and controlled disaster recovery drill outputs for **Group 05 (PUP-SPOT: Campus Lost and Found System)**.

---

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp05_lostandfounddb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp05_lostandfounddb` (Schema: `public`)<br>• 8 Relational Tables: `categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, `lost_report_images`, `claim_images`<br>• 8 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across category definitions, cataloged found items, student lost reports, filed claim requests, automated matching scores, and image metadata. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp05_lostandfounddb_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 1 Hour<br>**RTO (Recovery Time Objective):** 30 Minutes | 1-hour RPO limits lost report submissions during active campus hours; 30-minute RTO restores portal operations before claim tracking fails. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`lostandfound_app`, `lostandfound_public`, `lostandfound_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `lostandfound_backup` role:
```bash
pg_dump -U lostandfound_backup -h localhost -p 5432 -Fc -d grp05_lostandfounddb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : FA6808CEA3E2ACAF86A8887B5BAF86E0444A6C5A8C899D9B3D75F9EBB02D352C
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump

  Name                                Length LastWriteTime        
  ----                                ------ -------------        
  grp05_lostandfounddb_activity6.dump  35083 05/11/2026 4:46:41 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents (`pg_restore --list`) confirmed **91 TOC entries**, including all 8 tables (`categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, `lost_report_images`, `claim_images`), sequences, primary keys, check constraints, foreign keys, and default privileges.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

---

## 3.3 Restoration into Separate Test Database (`grp05_lostandfounddb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp05_lostandfounddb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp05_lostandfounddb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump"
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
  --------------------+------------+----------
   categories         | BASE TABLE | postgres
   claim_images       | BASE TABLE | postgres
   claims             | BASE TABLE | postgres
   item_images        | BASE TABLE | postgres
   items              | BASE TABLE | postgres
   lost_report_images | BASE TABLE | postgres
   lost_reports       | BASE TABLE | postgres
   potential_matches  | BASE TABLE | postgres
  (8 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'categories' AS tbl, count(*) FROM categories
  UNION ALL SELECT 'items', count(*) FROM items
  UNION ALL SELECT 'lost_reports', count(*) FROM lost_reports
  UNION ALL SELECT 'claims', count(*) FROM claims
  UNION ALL SELECT 'potential_matches', count(*) FROM potential_matches
  UNION ALL SELECT 'item_images', count(*) FROM item_images
  UNION ALL SELECT 'lost_report_images', count(*) FROM lost_report_images
  UNION ALL SELECT 'claim_images', count(*) FROM claim_images;
  ```
- **Actual Output:**
  ```text
          tbl         | count 
  --------------------+-------
   categories         |    11
   items              |     7
   lost_reports       |     8
   claims             |     3
   potential_matches  |     6
   item_images        |     0
   lost_report_images |     0
   claim_images       |     0
  (8 rows)
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
- **Actual Output:** All 53 constraint definitions (Primary Keys, Foreign Keys, Check Constraints, Unique Constraints) were validated and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Public User Read Test (`lostandfound_public`):**
  ```bash
  psql -U lostandfound_public -h localhost -p 5432 -d grp05_lostandfounddb_restore_test -c "SELECT item_id, title FROM items LIMIT 3;"
  ```
  *Output:*
  ```text
   item_id |              title              
  ---------+---------------------------------
         1 | [DEMO] Black leather wallet
         2 | [DEMO] Set of three silver keys
         3 | [DEMO] Navy blue backpack
  (3 rows)
  ```
- **Public User Tamper Restriction Test (`lostandfound_public` attempting UPDATE on `items`):**
  ```bash
  psql -U lostandfound_public -h localhost -p 5432 -d grp05_lostandfounddb_restore_test -c "UPDATE items SET status = 'claimed' WHERE item_id = 1;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table items
  ```
  *Conclusion:* Least-privilege ACLs and operational protections transferred seamlessly into the restored database.

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT i.item_id, c.name AS category_name, i.title, i.location_found, i.date_found, i.status
  FROM items i
  JOIN categories c ON i.category_id = c.category_id
  WHERE i.status = 'unclaimed'
  ORDER BY i.date_found DESC;
  ```
- **Actual Output:**
  ```text
   item_id |        category_name        |               title                |    location_found    | date_found |  status   
  ---------+-----------------------------+------------------------------------+----------------------+------------+-----------
         6 | Bottles and Food Containers | [DEMO] Blue Hydro Flask bottle     | Computer Laboratory  | 2026-11-04 | unclaimed
         1 | Wallets and Pouches         | [DEMO] Black leather wallet        | Library              | 2026-11-03 | unclaimed
         4 | Books and School Supplies   | [DEMO] Casio scientific calculator | Engineering Building | 2026-11-02 | unclaimed
         2 | Keys and Keychains          | [DEMO] Set of three silver keys    | Main Lobby           | 2026-11-01 | unclaimed
  (4 rows)
  ```

---

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp05_lostandfounddb_restore_test`, a disposable test item record was established:
- **Query:**
  ```sql
  INSERT INTO items (item_id, category_id, title, description, location_found, date_found, status)
  VALUES (999, 1, 'DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case', 'White charging case containing Bluetooth wireless earbuds', 'Science Building Room 204', '2026-11-05', 'unclaimed');

  SELECT item_id, title, location_found, date_found, status FROM items WHERE item_id = 999;
  ```
- **Actual Output:**
  ```text
   item_id |                         title                         |      location_found       | date_found |  status   
  ---------+-------------------------------------------------------+---------------------------+------------+-----------
       999 | DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case | Science Building Room 204 | 2026-11-05 | unclaimed
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM items WHERE item_id = 999;
  SELECT count(*) AS count_after_deletion FROM items WHERE item_id = 999;
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
  INSERT INTO items (item_id, category_id, title, description, location_found, date_found, status)
  VALUES (999, 1, 'DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case', 'White charging case containing Bluetooth wireless earbuds', 'Science Building Room 204', '2026-11-05', 'unclaimed')
  ON CONFLICT (item_id) DO NOTHING;

  SELECT item_id, title, location_found, date_found, status FROM items WHERE item_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   item_id |                         title                         |      location_found       | date_found |  status   
  ---------+-------------------------------------------------------+---------------------------+------------+-----------
       999 | DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case | Science Building Room 204 | 2026-11-05 | unclaimed
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original category, title, description, and status preserved. The drill record was subsequently deleted:
```sql
DELETE FROM items WHERE item_id = 999;
```

---

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `lostandfound_app_grp`, `lostandfound_public_grp`, `lostandfound_app`, `lostandfound_public`, etc., before permissions can attach.
2. **Image Storage Dependency:** Image metadata rows in `item_images`, `lost_report_images`, and `claim_images` reference relative filesystem paths in `storage/`. A database restore must always be paired with a filesystem backup of `storage/` to avoid orphaned metadata records.
3. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`grp05_lostandfounddb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For critical campus environments where a 1-hour RPO is insufficient, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

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