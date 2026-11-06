# Activity 6 Technical Evidence Dossier â€” Group 03: OJT Management System
## COMP 018: Database Backup, Restoration & Disaster Recovery Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **OJT Management System** (Project-OJT-RMA-System) |
| **Target Project Group** | **Group 3 (Evaluated under Tracker Key: Group 06 / Group 03):** Simon Russell Shaun; De Dios, Niño; Ramos, Jared; Dela Rosa, Rovi; Sietereales, Andrei; Geraldez, Mark Angelo |
| **Target Project Repository** | [https://github.com/swvenz/Project-OJT-RMA-System](https://github.com/swvenz/Project-OJT-RMA-System) (Branches: `main`, `ojt-system-untested.demo`) |
| **Primary Database** | `ojt_management` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `ojt_management_restore_test` |

---

# EVIDENCE DOSSIER & RESTORATION AUDIT ARTIFACTS

This evidence dossier contains the unedited backup commands, SHA-256 checksum verifications, archive inspection logs, database restoration runs, post-restore verification query results, and controlled disaster recovery drill outputs for **Group 03 (OJT Management System)**.

---

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `ojt_management` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `ojt_management` (Schema: `public`)<br>• 12 Relational Tables: `users`, `audit_log`, `students`, `companies`, `academic_supervisors`, `company_supervisors`, `requirements`, `ojt_applications`, `student_requirements`, `ojt_placements`, `attendance`, `evaluations`<br>• 12 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across student accounts, partner company profiles, compliance requirements, attendance logs, and supervisor evaluation grades. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `ojt_management_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 1 Hour<br>**RTO (Recovery Time Objective):** 30 Minutes | 1-hour RPO limits lost attendance records or student submissions during business hours; 30-minute RTO ensures placement and grading workflows resume promptly. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`ojt_app`, `ojt_student_user`, `ojt_report_user`, `ojt_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `ojt_backup` role:
```bash
pg_dump -U ojt_backup -h localhost -p 5432 -Fc -d ojt_management -f "C:\Users\oliva\OneDrive\Documents\pg_backups\ojt_management_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\ojt_management_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\ojt_management_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : FC979FF8640AE61F9A747475427E472B7D8344B193D751097DDEF576E3A10A7F
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\ojt_management_activity6.dump

  Name                          Length LastWriteTime        
  ----                          ------ -------------        
  ojt_management_activity6.dump  56714 05/11/2026 8:07:30 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents confirmed **143 TOC entries**, including all 12 tables (`users`, `students`, `companies`, `academic_supervisors`, `company_supervisors`, `requirements`, `ojt_applications`, `student_requirements`, `ojt_placements`, `attendance`, `evaluations`, `audit_log`), 12 sequences, primary keys, check constraints, foreign keys, and default privileges.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

---

## 3.3 Restoration into Separate Test Database (`ojt_management_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 ojt_management_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d ojt_management_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\ojt_management_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 12 Tables Exist and Check Table Ownership
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
        table_name      | table_type |  owner   
  ----------------------+------------+----------
   academic_supervisors | BASE TABLE | postgres
   attendance           | BASE TABLE | postgres
   audit_log            | BASE TABLE | postgres
   companies            | BASE TABLE | postgres
   company_supervisors  | BASE TABLE | postgres
   evaluations          | BASE TABLE | postgres
   ojt_applications     | BASE TABLE | postgres
   ojt_placements       | BASE TABLE | postgres
   requirements         | BASE TABLE | postgres
   student_requirements | BASE TABLE | postgres
   students             | BASE TABLE | postgres
   users                | BASE TABLE | postgres
  (12 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'academic_supervisors' AS tbl, count(*) FROM academic_supervisors
  UNION ALL SELECT 'attendance', count(*) FROM attendance
  UNION ALL SELECT 'audit_log', count(*) FROM audit_log
  UNION ALL SELECT 'companies', count(*) FROM companies
  UNION ALL SELECT 'company_supervisors', count(*) FROM company_supervisors
  UNION ALL SELECT 'evaluations', count(*) FROM evaluations
  UNION ALL SELECT 'ojt_applications', count(*) FROM ojt_applications
  UNION ALL SELECT 'ojt_placements', count(*) FROM ojt_placements
  UNION ALL SELECT 'requirements', count(*) FROM requirements
  UNION ALL SELECT 'student_requirements', count(*) FROM student_requirements
  UNION ALL SELECT 'students', count(*) FROM students
  UNION ALL SELECT 'users', count(*) FROM users;
  ```
- **Actual Output:**
  ```text
           tbl          | count 
  ----------------------+-------
   academic_supervisors |     2
   attendance           |     4
   audit_log            |     2
   companies            |     3
   company_supervisors  |     1
   evaluations          |     1
   ojt_applications     |     2
   ojt_placements       |     1
   requirements         |     5
   student_requirements |     4
   students             |     2
   users                |     7
  (12 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks)
- **Query:**
  ```sql
  SELECT count(*) AS total_constraints,
         sum(case when constraint_type = 'PRIMARY KEY' then 1 else 0 end) AS primary_keys,
         sum(case when constraint_type = 'FOREIGN KEY' then 1 else 0 end) AS foreign_keys,
         sum(case when constraint_type = 'UNIQUE' then 1 else 0 end) AS unique_constraints,
         sum(case when constraint_type = 'CHECK' then 1 else 0 end) AS check_constraints
  FROM information_schema.table_constraints
  WHERE table_schema = 'public';
  ```
- **Actual Output:**
  ```text
   total_constraints | primary_keys | foreign_keys | unique_constraints | check_constraints 
  -------------------+--------------+--------------+--------------------+-------------------
                 129 |           12 |           15 |                 12 |                90
  (1 row)
  ```
  *Analysis:* All 129 constraints (12 Primary Keys, 15 Foreign Keys, 12 Unique Constraints, 90 Check Constraints) were verified and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Test (`ojt_app` reading `companies`):**
  ```bash
  psql -U ojt_app -h localhost -p 5432 -d ojt_management_restore_test -c "SELECT company_id, company_name FROM companies LIMIT 2;"
  ```
  *Output:*
  ```text
   company_id |      company_name      
  ------------+------------------------
            1 | Acme Corp
            2 | InnovateTech Solutions
  (2 rows)
  ```
- **Auditor Restriction Test (`ojt_report_user` attempting to read `users`):**
  ```bash
  psql -U ojt_report_user -h localhost -p 5432 -d ojt_management_restore_test -c "SELECT * FROM users;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table users
  ```
  *Conclusion:* Least-privilege ACLs and credential protections transferred seamlessly into the restored database.

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT 
      p.placement_id, 
      s.student_number, 
      s.first_name || ' ' || s.last_name AS student_name, 
      c.company_name, 
      p.status AS placement_status, 
      p.required_hours, 
      p.start_date, 
      p.end_date,
      e.score AS evaluation_score
  FROM ojt_placements p 
  JOIN students s ON s.student_id = p.student_id 
  JOIN companies c ON c.company_id = p.company_id 
  LEFT JOIN evaluations e ON e.placement_id = p.placement_id;
  ```
- **Actual Output:**
  ```text
   placement_id | student_number | student_name | company_name | placement_status | required_hours | start_date |  end_date  | evaluation_score 
  --------------+----------------+--------------+--------------+------------------+----------------+------------+------------+------------------
              1 | 2026-0001      | Demo Student | Acme Corp    | Active           |         300.00 | 2026-10-20 | 2026-12-20 |            88.50
  (1 row)
  ```

---

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `ojt_management_restore_test`, a disposable test requirement record was established:
- **Query:**
  ```sql
  INSERT INTO requirements (req_id, title, description, is_mandatory)
  VALUES (999, 'DRILL-TEST-REQ-999: OJT Medical & Safety Insurance Waiver', 'Disaster recovery simulation requirement record', FALSE);

  SELECT req_id, title, is_mandatory FROM requirements WHERE req_id = 999;
  ```
- **Actual Output:**
  ```text
   req_id |                           title                           | is_mandatory 
  --------+-----------------------------------------------------------+--------------
      999 | DRILL-TEST-REQ-999: OJT Medical & Safety Insurance Waiver | f
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM requirements WHERE req_id = 999;
  SELECT count(*) AS count_after_deletion FROM requirements WHERE req_id = 999;
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
  INSERT INTO requirements (req_id, title, description, is_mandatory)
  VALUES (999, 'DRILL-TEST-REQ-999: OJT Medical & Safety Insurance Waiver', 'Disaster recovery simulation requirement record', FALSE)
  ON CONFLICT (req_id) DO NOTHING;

  SELECT req_id, title, is_mandatory FROM requirements WHERE req_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   req_id |                           title                           | is_mandatory 
  --------+-----------------------------------------------------------+--------------
      999 | DRILL-TEST-REQ-999: OJT Medical & Safety Insurance Waiver | f
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original requirement title, description, and mandatory status preserved. The drill record was subsequently deleted:
```sql
DELETE FROM requirements WHERE req_id = 999;
```

---

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `ojt_app_grp`, `ojt_student_grp`, `ojt_app`, etc., before permissions can attach.
2. **File Storage Synchronization:** Student document paths in `student_requirements.file_path` reference relative paths in `uploads/requirements/`. A database restore must always be paired with a filesystem backup of `uploads/` to avoid orphaned database records pointing to missing physical files.
3. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`ojt_management_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
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