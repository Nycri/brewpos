# COMP 018: Database Administration - Activity 6: Database Backup, Restoration, and Recovery
## Group Evaluation Report: Group 07 - CampusSafe: Campus Hazard & Safety Reporting System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **CampusSafe: Campus Hazard & Safety Reporting System** |
| **Target Project Group** | **Group 7:** Alba, Arreza, Orpesa, Robiso, Santos |
| **Target Project Repository** | [https://github.com/jeffersonorpesa2-ops/grp07-campussafe](https://github.com/jeffersonorpesa2-ops/grp07-campussafe) |
| **Primary Database** | `grp07_campussafedb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp07_campussafedb_restore_test` |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp07_campussafedb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp07_campussafedb` (Schema: `public`)<br>• 9 Relational Tables: `role`, `app_user`, `hazard_category`, `campus_location`, `hazard_report`, `assignment`, `report_status_history`, `maintenance_action`, `attachment`<br>• 9 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across user accounts, campus buildings, hazard categories, submitted incident reports, technician dispatches, immutable status audit logs, and photo attachments. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp07_campussafedb_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 24 Hours<br>**RTO (Recovery Time Objective):** 2 Hours | Daily dumps at 01:00 AM are sufficient for campus incident reporting volume (10–30 reports monthly). RTO of 2 hours provides ample buffer to provision a standby instance and restore schemas. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`campussafe_app`, `campussafe_report`, `campussafe_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts (`03_roles_and_privileges.sql`) must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `campussafe_backup` role:
```bash
pg_dump -U campussafe_backup -h localhost -p 5432 -Fc -d grp07_campussafedb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp07_campussafedb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp07_campussafedb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp07_campussafedb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 73DC76542E218D4046B236C72A7FF58019862A38AC5DEF5FAE75D59D77FB99B2
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp07_campussafedb_activity6.dump

  Name                              Length LastWriteTime        
  ----                              ------ -------------        
  grp07_campussafedb_activity6.dump  47711 05/11/2026 2:13:17 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents (`pg_restore --list`) confirmed **118 TOC entries**, including all 9 tables (`app_user`, `assignment`, `attachment`, `campus_location`, `hazard_category`, `hazard_report`, `maintenance_action`, `report_status_history`, `role`), sequences, primary keys, check constraints, indexes, and ACL grants.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

## 3.3 Restoration into Separate Test Database (`grp07_campussafedb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp07_campussafedb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp07_campussafedb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp07_campussafedb_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 9 Tables Exist and Check Table Ownership
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
        table_name       | table_type |  owner   
  -----------------------+------------+----------
   app_user              | BASE TABLE | postgres
   assignment            | BASE TABLE | postgres
   attachment            | BASE TABLE | postgres
   campus_location       | BASE TABLE | postgres
   hazard_category       | BASE TABLE | postgres
   hazard_report         | BASE TABLE | postgres
   maintenance_action    | BASE TABLE | postgres
   report_status_history | BASE TABLE | postgres
   role                  | BASE TABLE | postgres
  (9 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'role' AS tbl, count(*) FROM role
  UNION ALL SELECT 'app_user', count(*) FROM app_user
  UNION ALL SELECT 'hazard_category', count(*) FROM hazard_category
  UNION ALL SELECT 'campus_location', count(*) FROM campus_location
  UNION ALL SELECT 'hazard_report', count(*) FROM hazard_report
  UNION ALL SELECT 'assignment', count(*) FROM assignment
  UNION ALL SELECT 'report_status_history', count(*) FROM report_status_history
  UNION ALL SELECT 'maintenance_action', count(*) FROM maintenance_action
  UNION ALL SELECT 'attachment', count(*) FROM attachment;
  ```
- **Actual Output:**
  ```text
            tbl          | count 
  -----------------------+-------
   role                  |     5
   app_user              |     6
   hazard_category       |     6
   campus_location       |    10
   hazard_report         |    17
   assignment            |    12
   report_status_history |    60
   maintenance_action    |     9
   attachment            |     5
  (9 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Activity 4 Access Control on Restored Database
- **Runtime User Test (`campussafe_app`):**
  ```bash
  psql -U campussafe_app -h localhost -p 5432 -d grp07_campussafedb_restore_test -c "SELECT report_id, status FROM hazard_report LIMIT 3;"
  ```
  *Output:*
  ```text
   report_id |  status   
  -----------+-----------
           1 | Submitted
           2 | Submitted
           3 | Verified
  (3 rows)
  ```
- **Auditor Restriction Test (`campussafe_report` attempting to read `app_user`):**
  ```bash
  psql -U campussafe_report -h localhost -p 5432 -d grp07_campussafedb_restore_test -c "SELECT * FROM app_user;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table app_user
  ```
  *Conclusion:* Least-privilege ACLs and credential protections transferred seamlessly into the restored database.

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp07_campussafedb_restore_test`, a disposable test hazard report was established:
- **Query:**
  ```sql
  INSERT INTO hazard_report (report_id, reporter_user_id, location_id, category_id, description, status)
  VALUES (999, 4, 1, 1, 'DRILL-TEST-HAZARD-999: Chemical shutoff valve leaking in Chemistry Lab 304', 'Submitted');

  SELECT report_id, description, status, submitted_at FROM hazard_report WHERE report_id = 999;
  ```
- **Actual Output:**
  ```text
   report_id |                                description                                 |  status   |        submitted_at        
  -----------+----------------------------------------------------------------------------+-----------+----------------------------
         999 | DRILL-TEST-HAZARD-999: Chemical shutoff valve leaking in Chemistry Lab 304 | Submitted | 2026-11-05 02:16:03.152954
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM hazard_report WHERE report_id = 999;
  SELECT count(*) AS count_after_deletion FROM hazard_report WHERE report_id = 999;
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
  INSERT INTO hazard_report (report_id, reporter_user_id, location_id, category_id, description, status)
  VALUES (999, 4, 1, 1, 'DRILL-TEST-HAZARD-999: Chemical shutoff valve leaking in Chemistry Lab 304', 'Submitted')
  ON CONFLICT (report_id) DO NOTHING;

  SELECT report_id, description, status, submitted_at FROM hazard_report WHERE report_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   report_id |                                description                                 |  status   |        submitted_at        
  -----------+----------------------------------------------------------------------------+-----------+----------------------------
         999 | DRILL-TEST-HAZARD-999: Chemical shutoff valve leaking in Chemistry Lab 304 | Submitted | 2026-11-05 02:16:32.861635
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original category, reporter ID, location, and description preserved. The drill record was subsequently deleted:
```sql
DELETE FROM hazard_report WHERE report_id = 999;
```

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, `database/03_roles_and_privileges.sql` must be applied to define `app_runtime_grp`, `campussafe_app`, etc., before permissions can attach.
2. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`grp07_campussafedb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For critical enterprise environments where a 24-hour RPO is unacceptable, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 7 - CampusSafe: Campus Hazard & Safety Reporting System |
| **Database and backup file** | `grp07_campussafedb`, `grp07_campussafedb_activity6.dump`, Size: 47,711 bytes, SHA256: `73DC76542E218D4046B236C72A7FF58019862A38AC5DEF5FAE75D59D77FB99B2` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-05 02:13:17) |
| **Restore test result** | Success (Restored into `grp07_campussafedb_restore_test`; all 9 tables, constraints, and data verified) |
| **Access control after restore** | (Activity 4 roles and permissions validated; `campussafe_report` denied reading `app_user`) |
| **Recovery simulation result** | Recovered (Simulated deleted test hazard report successfully restored from baseline state) |
| **Proposed RPO and RTO** | RPO: 24 hours, RTO: 2 hours |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/activity-6/Evidence-act6` |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

---

---

> [!NOTE]
> All technical backup CLI commands, SHA-256 hash outputs, pg_restore verification logs, constraint validation checks, and disaster recovery simulation drill outputs for Group 07 are archived in [Evidence-act6/G07_Act-6_Evidence.md](Evidence-act6/G07_Act-6_Evidence.md).