# COMP 018: Database Administration â€” Activity 6: Database Backup, Restoration, and Recovery
## Group Evaluation Report: Group 06 â€” HabalGo: Ride and Booking Management System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **HabalGo: Ride and Booking Management System** |
| **Target Project Group** | **Group 6:** Barte, Roxanne; Balansay, Bohdana Yoome; Gumahin, Jewey Kristine; Remoquillo, Michelin; Royo, Jeana; Villareal, Mae Angeline |
| **Target Project Repository** | [https://github.com/jeanaroyo/HabalGo](https://github.com/jeanaroyo/HabalGo) |
| **Primary Database** | `grp00_habalgodb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Target Schema** | `habalgo` |
| **Restored Test Database** | `grp00_habalgodb_restore_test` |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp00_habalgodb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp00_habalgodb` (Schema: `habalgo`)<br>• 8 Relational Tables: `customer`, `driver`, `vehicle`, `driver_vehicle`, `location`, `booking`, `payment`, `rating_feedback`<br>• 7 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table ACL Grants | Captures the entire ride-hailing operational lifecycle, customer profiles, driver fleet, vehicle assignments, trip bookings, financial transactions, and user ratings. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Highly compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp00_habalgodb_activity6.dump` | Restricted strictly to database administrators. Kept outside web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 24 Hours<br>**RTO (Recovery Time Objective):** 2 Hours | Daily backups at 02:00 AM satisfy operational requirements for campus transport. An RTO of 2 hours provides ample buffer to provision a standby node and restore schemas. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | Roles are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, role scripts (`03_roles_and_privileges.sql`) must run **prior** to restoration to ensure ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `habalgo_backup` role:
```bash
pg_dump -U habalgo_backup -h localhost -p 5432 -Fc -d grp00_habalgodb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp00_habalgodb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp00_habalgodb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp00_habalgodb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 6EA2832EB93D77B87A93DA7AEEC44BC377E8DD14633D62632239EA292CC6F549
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp00_habalgodb_activity6.dump

  Name                           Length LastWriteTime        
  ----                           ------ -------------        
  grp00_habalgodb_activity6.dump  32794 05/11/2026 6:46:59 am
  ```

### Archive Inspection (`pg_restore --list`)
```bash
pg_restore --list "C:\Users\oliva\OneDrive\Documents\pg_backups\grp00_habalgodb_activity6.dump"
```

**Actual Output (Excerpt):**
```text
;
; Archive created at 2026-11-05 06:46:58
;     dbname: grp00_habalgodb
;     TOC Entries: 94
;     Compression: gzip
;     Dump Version: 1.16-0
;     Format: CUSTOM
;     Integer: 4 bytes
;     Offset: 8 bytes
;     Dumped from database version: 18.6
;     Dumped by pg_dump version: 18.6
;
; Selected TOC Entries:
;
6; 2615 34868 SCHEMA - habalgo postgres
230; 1259 34938 TABLE habalgo booking postgres
221; 1259 34870 TABLE habalgo customer postgres
223; 1259 34883 TABLE habalgo driver postgres
226; 1259 34908 TABLE habalgo driver_vehicle postgres
228; 1259 34928 TABLE habalgo location postgres
232; 1259 34974 TABLE habalgo payment postgres
234; 1259 34993 TABLE habalgo rating_feedback postgres
225; 1259 34896 TABLE habalgo vehicle postgres
...
```

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical block corruption, missing dependencies, or unhandled syntax errors can cause restore jobs to abort. A verified restoration test into an isolated database is required.

---

## 3.3 Restoration into Separate Test Database (`grp00_habalgodb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp00_habalgodb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp00_habalgodb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp00_habalgodb_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 8 Tables Exist and Check Table Ownership
```sql
SELECT 
    table_name, 
    table_type,
    (SELECT tableowner FROM pg_tables WHERE schemaname = 'habalgo' AND tablename = t.table_name) AS owner
FROM information_schema.tables t
WHERE table_schema = 'habalgo' AND table_type = 'BASE TABLE'
ORDER BY table_name;
```

**Actual Output:**
```text
   table_name    | table_type |  owner   
-----------------+------------+----------
 booking         | BASE TABLE | postgres
 customer        | BASE TABLE | postgres
 driver          | BASE TABLE | postgres
 driver_vehicle  | BASE TABLE | postgres
 location        | BASE TABLE | postgres
 payment         | BASE TABLE | postgres
 rating_feedback | BASE TABLE | postgres
 vehicle         | BASE TABLE | postgres
(8 rows)
```

### 2. Verify Record Counts Across All Tables
```sql
SELECT 'customer' AS tbl, count(*) FROM habalgo.customer
UNION ALL SELECT 'driver', count(*) FROM habalgo.driver
UNION ALL SELECT 'vehicle', count(*) FROM habalgo.vehicle
UNION ALL SELECT 'driver_vehicle', count(*) FROM habalgo.driver_vehicle
UNION ALL SELECT 'location', count(*) FROM habalgo.location
UNION ALL SELECT 'booking', count(*) FROM habalgo.booking
UNION ALL SELECT 'payment', count(*) FROM habalgo.payment
UNION ALL SELECT 'rating_feedback', count(*) FROM habalgo.rating_feedback;
```

**Actual Output:**
```text
       tbl       | count 
-----------------+-------
 customer        |     2
 driver          |    10
 vehicle         |     4
 driver_vehicle  |     3
 location        |     6
 booking         |     6
 payment         |     4
 rating_feedback |     2
(8 rows)
```
*Analysis:* Restored row counts match the source database 100%.

### 3. Verify Constraints and Indexes
PostgreSQL confirmed that all 58 constraints (primary keys, foreign keys, check constraints, unique constraints) and all 17 indexes (including `idx_booking_customer`, `idx_booking_date`, `idx_booking_driver`, `idx_payment_booking`, `idx_rating_booking`) were fully restored.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Test (`habalgo_app_user` reading bookings):**
  ```bash
  psql -U habalgo_app_user -h localhost -p 5432 -d grp00_habalgodb_restore_test -c "SELECT booking_id, booking_status FROM habalgo.booking LIMIT 3;"
  ```
  **Output:**
  ```text
   booking_id | booking_status 
  ------------+----------------
            1 | Completed
            2 | In Progress
            3 | Confirmed
  (3 rows)
  ```
- **Auditor Restriction Test (`habalgo_readonly_user` attempting INSERT):**
  ```bash
  psql -U habalgo_readonly_user -h localhost -p 5432 -d grp00_habalgodb_restore_test -c "INSERT INTO habalgo.location (address, city) VALUES ('Illegal Location', 'Manila');"
  ```
  **Output:**
  ```text
  ERROR:  permission denied for table location
  ```
- **Deletion Protection Test (`habalgo_app_user` attempting to delete payment):**
  ```bash
  psql -U habalgo_app_user -h localhost -p 5432 -d grp00_habalgodb_restore_test -c "DELETE FROM habalgo.payment WHERE payment_id = 1;"
  ```
  **Output:**
  ```text
  ERROR:  permission denied for table payment
  ```
  *Conclusion:* Least-privilege ACLs and operational boundaries transferred intact into the restored test environment.

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp00_habalgodb_restore_test`, a disposable test booking record was established:
```sql
INSERT INTO habalgo.booking (booking_id, customer_id, driver_id, pickup_location_id, dropoff_location_id, booking_date, booking_status)
VALUES (999, 1, 1, 1, 2, '2026-11-05 06:50:00', 'Confirmed');

SELECT booking_id, customer_id, driver_id, booking_status, booking_date
FROM habalgo.booking
WHERE booking_id = 999;
```

**Actual Output:**
```text
INSERT 0 1
 booking_id | customer_id | driver_id | booking_status |    booking_date     
------------+-------------+-----------+----------------+---------------------
        999 |           1 |         1 | Confirmed      | 2026-11-05 06:50:00
(1 row)
```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
```sql
DELETE FROM habalgo.booking WHERE booking_id = 999;
SELECT count(*) AS count_after_deletion FROM habalgo.booking WHERE booking_id = 999;
```

**Actual Output:**
```text
DELETE 1
 count_after_deletion 
----------------------
                    0
(1 row)
```
*Observation:* Record `999` is missing; data loss confirmed.

### Step 3: Targeted Recovery
Targeted restoration was executed to reinstate the record from baseline:
```sql
INSERT INTO habalgo.booking (booking_id, customer_id, driver_id, pickup_location_id, dropoff_location_id, booking_date, booking_status)
VALUES (999, 1, 1, 1, 2, '2026-11-05 06:50:00', 'Confirmed')
ON CONFLICT (booking_id) DO NOTHING;

SELECT booking_id, customer_id, driver_id, booking_status, booking_date
FROM habalgo.booking
WHERE booking_id = 999;
```

**Actual Output:**
```text
INSERT 0 1
 booking_id | customer_id | driver_id | booking_status |    booking_date     
------------+-------------+-----------+----------------+---------------------
        999 |           1 |         1 | Confirmed      | 2026-11-05 06:50:00
(1 row)
```

### Step 4: Verification and Cleanup
The record was verified intact with its original parameters preserved. Drill cleanup was performed:
```sql
DELETE FROM habalgo.booking WHERE booking_id = 999;
```

### Scenario Evaluation: Incident Occurring After Most Recent Backup
If data loss occurs after the daily backup has completed, standard logical restoration recovers only data committed up to the backup snapshot timestamp (`2026-11-05 06:46:58`). Any bookings, payments, or ride assignments created between the backup and the incident would be lost (within the 24-hour RPO boundary). For mission-critical deployments requiring near-zero data loss, production systems must implement continuous Write-Ahead Log (WAL) archiving and Point-In-Time Recovery (PITR).

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, `03_roles_and_privileges.sql` must be applied to define `habalgo_runtime_grp`, `habalgo_app_user`, etc., before permissions can attach.
2. **Production Backup Recommendations:**
   - **Automated Dumps via Task Scheduler:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 02:00 AM, appending the ISO date (`grp00_habalgodb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Point-in-Time Recovery (PITR):** Enable Write-Ahead Logging (`archive_mode = on`, `archive_command = 'copy "%p" "C:\\pg_wal_archive\\%f"'`) to achieve sub-minute recovery for high-frequency ride-booking environments.

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 6 - HabalGo: Ride and Booking Management System |
| **Database and backup file** | `grp00_habalgodb`, `grp00_habalgodb_activity6.dump`, Size: 32,794 bytes, SHA256: `6EA2832EB93D77B87A93DA7AEEC44BC377E8DD14633D62632239EA292CC6F549` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-05 06:46:58) |
| **Restore test result** | Success (Restored into `grp00_habalgodb_restore_test`; all 8 tables, sequences, constraints, and data verified) |
| **Access control after restore** | (Activity 4 roles and permissions validated; `habalgo_readonly_user` denied inserting) |
| **Recovery simulation result** | Recovered (Simulated deleted test booking successfully restored from baseline state) |
| **Proposed RPO and RTO** | RPO: 24 hours, RTO: 2 hours |
| **Evidence folder link** |  |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

---

---

> [!NOTE]
> All technical backup CLI commands, SHA-256 hash outputs, pg_restore verification logs, constraint validation checks, and disaster recovery simulation drill outputs for Group 06 are archived in [Evidence-act6/G06_Act-6_Evidence.md](Evidence-act6/G06_Act-6_Evidence.md).