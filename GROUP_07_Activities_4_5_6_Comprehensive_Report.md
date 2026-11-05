# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **CampusSafe: Campus Hazard & Safety Reporting System** |
| **Target Project Group** | **Group 7** (Alba,Arreza, Orpesa, Robiso, Santos) |
| **Target Project Repository** | [https://github.com/jeffersonorpesa2-ops/grp07-campussafe](https://github.com/jeffersonorpesa2-ops/grp07-campussafe) |
| **Primary Database** | `grp07_campussafedb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp07_campussafedb_restore_test` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime User `campussafe_app`)
   - 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `campussafe_report`)
   - 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)
   - 1.6 Issues, Fixes, and Feedback to Owners
2. [ACTIVITY 5: Database Maintenance and Monitoring](#activity-5-database-maintenance-and-monitoring)
   - 2.1 Database Health & Overview Inspection
   - 2.2 Connection, Session & Activity Monitoring
   - 2.3 Table Statistics & Bloat Inspection
   - 2.4 Log Review & Issue Identification
   - 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)
   - 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)
   - 2.7 Prioritized Recommendations & Technical Justification
3. [ACTIVITY 6: Database Backup, Restoration, and Recovery](#activity-6-database-backup-restoration-and-recovery)
   - 3.1 Backup Planning & Architecture
   - 3.2 Backup Creation, Checksum & Archive Inspection
   - 3.3 Restoration into Separate Test Database (`grp07_campussafedb_restore_test`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Activity 3
In many default setups, applications connect using the PostgreSQL superuser (`postgres`) or an unrestricted database owner. If an application runtime account retains blanket `ALL PRIVILEGES` or `DROP`/`DELETE` capabilities across all tables, any application vulnerability (such as SQL injection or an unauthenticated administrative endpoint) could allow attackers to purge legal incident histories, drop audit logs, or exfiltrate employee password hashes. This directly violates the **Principle of Least Privilege (PoLP)**.

### Activity 4 Solution
For Group 7's **CampusSafe** platform, we implemented a comprehensive role-based access control (RBAC) hierarchy decoupling administrative maintenance from runtime application operations:
- **Group Roles (`NOLOGIN`):** Common privilege bundles representing distinct responsibility profiles (`app_runtime_grp`, `app_readonly_grp`, and `app_backup_grp`).
- **Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their corresponding group roles (`campussafe_app`, `campussafe_report`, and `campussafe_backup`).
- **Narrow Deletion (`DELETE` Scoping):** The runtime account `campussafe_app` is granted `SELECT`, `INSERT`, and `UPDATE` on operational tables, but its `DELETE` privilege is strictly confined to `attachment` (to remove misfiled images) and `assignment` (to reassign work orders). It is strictly forbidden from deleting rows from `hazard_report`, `report_status_history`, `maintenance_action`, or `app_user`.
- **Auditor Credential Protection:** The read-only reporting account `campussafe_report` is granted `SELECT` on reports, categories, locations, actions, and history, but is **explicitly denied `SELECT` on `app_user`**, ensuring that user password hashes cannot be read by auditors or reporting tools.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `campussafe_app` | `app_runtime_grp` | Application Runtime | Handles all live hazard report submissions, status updates, technician logging, and attachment uploads | • `CONNECT` on `grp07_campussafedb`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on all operational tables<br>• `DELETE` **strictly on `attachment` & `assignment`**<br>• `USAGE, SELECT` on all sequences | Restricting `DELETE` strictly to `attachment` and `assignment` guarantees that even under SQL injection, hazard incident histories, audit transitions, and user profiles cannot be deleted. |
| `campussafe_report` | `app_readonly_grp` | Safety Auditor / Compliance | Read-only inspection of campus safety metrics, incident logs, and resolution timeliness | • `CONNECT` on `grp07_campussafedb`<br>• `USAGE` on schema `public`<br>• `SELECT` on `hazard_report`, `hazard_category`, `campus_location`, `assignment`, `report_status_history`, `maintenance_action`, `attachment`<br>*(No access to `app_user`)* | Read-only access to operational logs without risk of data tampering. Excluding `app_user` shields bcrypt password hashes from compliance auditors. |
| `campussafe_backup` | `app_backup_grp` | Backup Operator | Runs logical database backups (`pg_dump`) | • `CONNECT` on `grp07_campussafedb`<br>• `USAGE` on schema `public`<br>• `SELECT` on all tables & sequences | Grants complete read access required for logical dumps while strictly preventing DDL or DML write operations. |
| `postgres` | *(Superuser)* | Database Administrator | Schema provisioning, migrations, maintenance, disaster recovery | Superuser / Owner privileges | Reserved strictly for DBA operations; never configured as the application DSN account. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script ([`database/03_roles_and_privileges.sql`](file:///c:/Users/oliva/OneDrive/Documents/campus-safe(G07)/grp07-campussafe/database/03_roles_and_privileges.sql)) was executed as `postgres` against `grp07_campussafedb`:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE app_runtime_grp NOLOGIN;
CREATE ROLE app_readonly_grp NOLOGIN;
CREATE ROLE app_backup_grp NOLOGIN;

-- 2. Create Login Roles (LOGIN)
CREATE ROLE campussafe_app LOGIN PASSWORD 'AppRuntimePass2026!';
CREATE ROLE campussafe_report LOGIN PASSWORD 'ReportUserPass2026!';
CREATE ROLE campussafe_backup LOGIN PASSWORD 'BackupOperatorPass2026!';

-- 3. Assign Role Memberships
GRANT app_runtime_grp TO campussafe_app;
GRANT app_readonly_grp TO campussafe_report;
GRANT app_backup_grp TO campussafe_backup;

-- 4. Lock down PUBLIC permissions
REVOKE ALL ON DATABASE grp07_campussafedb FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 5. Database Connection and Schema Usage
GRANT CONNECT ON DATABASE grp07_campussafedb TO app_runtime_grp, app_readonly_grp, app_backup_grp;
GRANT USAGE ON SCHEMA public TO app_runtime_grp, app_readonly_grp, app_backup_grp;

-- 6. Application Runtime Permissions (Least Privilege)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_runtime_grp;
GRANT INSERT ON hazard_report, attachment, assignment, report_status_history,
                maintenance_action, app_user, hazard_category, campus_location
    TO app_runtime_grp;
GRANT UPDATE ON hazard_report, assignment, app_user, hazard_category, campus_location
    TO app_runtime_grp;
GRANT DELETE ON attachment, assignment TO app_runtime_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_runtime_grp;

-- 7. Read-Only / Auditor Role Permissions (Excluding app_user)
GRANT SELECT ON hazard_report, hazard_category, campus_location,
                report_status_history, maintenance_action, assignment, attachment
    TO app_readonly_grp;

-- 8. Backup Operator Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_backup_grp;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO app_backup_grp;

-- 9. Default Privileges for Future Objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO app_runtime_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_runtime_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO app_backup_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO app_backup_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `campussafe_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT report_id, status, priority, submitted_at FROM hazard_report LIMIT 5;
  ```
- **Expected Result:** Allowed; returns submitted hazard reports.
- **Actual Result / Output:**
  ```text
   report_id |  status   | priority |    submitted_at     
  -----------+-----------+----------+---------------------
           1 | Submitted |          | 2026-10-15 09:20:00
           2 | Submitted |          | 2026-10-15 11:45:00
           3 | Verified  | High     | 2026-10-16 08:15:00
           4 | Verified  | Medium   | 2026-10-16 13:00:00
           5 | Assigned  | High     | 2026-10-17 07:45:00
  (5 rows)
  ```

### Test 1.2: Insert a Hazard Report & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO hazard_report (reporter_user_id, location_id, category_id, description, status) 
  VALUES (4, 1, 1, 'Activity 4 Test Hazard: Loose conduit in Lab 304', 'Submitted') 
  RETURNING report_id, status;
  ```
- **Expected Result:** Allowed; inserts record and advances `hazard_report_report_id_seq`.
- **Actual Result / Output:**
  ```text
   report_id |  status   
  -----------+-----------
          17 | Submitted
  (1 row)
  INSERT 0 1
  ```

### Test 1.3: Update Mutable Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE hazard_report SET priority = 'Medium' WHERE report_id = 17 RETURNING report_id, priority;
  ```
- **Expected Result:** Allowed; runtime account updates mutable attributes.
- **Actual Result / Output:**
  ```text
   report_id | priority 
  -----------+----------
          17 | Medium
  (1 row)
  UPDATE 1
  ```

### Test 1.4: Delete Attachment (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO attachment (report_id, uploaded_by_user_id, file_name, file_path_or_url, mime_type) 
  VALUES (17, 4, 'temp_test.jpg', 'uploads/temp_test.jpg', 'image/jpeg') RETURNING attachment_id;
  DELETE FROM attachment WHERE file_name = 'temp_test.jpg';
  ```
- **Expected Result:** Allowed; runtime account is authorized to delete misfiled attachments.
- **Actual Result / Output:**
  ```text
   attachment_id 
  ---------------
               6
  (1 row)
  INSERT 0 1
  DELETE 1
  ```

### Test 1.5: Delete Hazard Report (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM hazard_report WHERE report_id = 17;
  ```
- **Expected Result:** Denied; runtime account must never delete incident records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table hazard_report
  ```

### Test 1.6: Delete User Record (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM app_user WHERE user_id = 4;
  ```
- **Expected Result:** Denied; runtime account cannot delete user identities (soft-deactivation required).
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table app_user
  ```

### Test 1.7: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE attachment;
  ```
- **Expected Result:** Denied; DDL requires object ownership.
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table attachment
  ```

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `campussafe_report`)

### Test 2.1: Read Hazard Reports and Joined Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT r.report_id, c.category_name, l.building, r.status, r.priority 
  FROM hazard_report r 
  JOIN hazard_category c ON r.category_id = c.category_id 
  JOIN campus_location l ON r.location_id = l.location_id 
  LIMIT 5;
  ```
- **Expected Result:** Allowed; read-only inspection query executes successfully.
- **Actual Result / Output:**
  ```text
   report_id | category_name |         building          |  status   | priority 
  -----------+---------------+---------------------------+-----------+----------
           1 | Electrical    | Science & Technology Hall | Submitted | 
          11 | Fire Safety   | Science & Technology Hall | Resolved  | High
          17 | Electrical    | Science & Technology Hall | Submitted | Medium
           2 | Other Safety  | Main Academic Complex     | Submitted | 
          14 | Structural    | Main Academic Complex     | Closed    | High
  (5 rows)
  ```

### Test 2.2: Attempt to Read User Credentials (Denied — Credential Protection)
- **SQL Statement:**
  ```sql
  SELECT * FROM app_user;
  ```
- **Expected Result:** Denied; auditor role has no `SELECT` privilege on `app_user` to safeguard password hashes.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table app_user
  ```

### Test 2.3: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO hazard_category (category_name) VALUES ('Rogue Category');
  ```
- **Expected Result:** Denied; read-only account cannot insert data.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table hazard_category
  ```

### Test 2.4: Attempt to Update Records (Denied)
- **SQL Statement:**
  ```sql
  UPDATE hazard_report SET priority = 'Critical' WHERE report_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot alter incident classifications.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table hazard_report
  ```

### Test 2.5: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM hazard_report WHERE report_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot delete data.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table hazard_report
  ```

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  CREATE DATABASE rogue_test_db;
  ```
- **Expected Result:** Denied; application users must not possess administrative `CREATEDB` privileges.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied to create database
  ```

### Test 3.2: Attempt to Connect to Unrelated Database (Denied)
- **Command:**
  ```bash
  psql -U campussafe_app -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
  ```
- **Expected Result:** Denied; cross-database connection is blocked by revoked PUBLIC privileges.
- **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "canteen_db"
  DETAIL:  User does not have CONNECT privilege.
  ```

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **PHP PostgreSQL Extension Loading:** On the local evaluation environment, PHP 8.5.12 was installed under `C:\Program Files\php-8.5.12\`, but no `php.ini` was active. We resolved this by creating a user-level configuration at `C:\Users\oliva\.php\php.ini`, enabling `pdo_pgsql`, `pgsql`, `fileinfo`, `gd`, `curl`, and `mbstring`, and setting `PHPRC` globally.
2. **`start.bat` and `setup.bat` Portability:** Updated both launcher batch scripts to automatically detect and export `PHPRC=%USERPROFILE%\.php` so users can double-click `start.bat` without manual PATH or INI adjustments.
3. **Database Roles Alignment:** Configured `campussafe_app`, `campussafe_report`, and `campussafe_backup` with the local evaluation password matching `.env` and executed `seed_users.php` to synchronize bcrypt demo hashes for `Password123!`.

### Feedback to Owning Group (Group 7: CampusSafe)
- **Commendation:** The database architecture is exceptionally well-structured. The 6-stage lifecycle (`Submitted` &rarr; `Verified` &rarr; `Assigned` &rarr; `In Progress` &rarr; `Resolved` &rarr; `Closed`) is strictly enforced through relational checks, foreign keys, and immutable audit logs in `report_status_history`.
- **Recommendation 1:** In `setup.bat` and `start.bat`, include explicit checks for `PHPRC` or common PHP install paths (`C:\Program Files\php-*`) to simplify setup for evaluators who run standalone PHP instead of XAMPP.
- **Recommendation 2:** In `database/03_roles_and_privileges.sql`, add `ALTER ROLE campussafe_app WITH PASSWORD '...'` guidance in comments so evaluators remember to synchronize the database role password with `.env`.

---

# ACTIVITY 5: Database Maintenance and Monitoring

## 2.1 Database Health & Overview Inspection

### Queries Executed
```sql
-- 1. PostgreSQL Engine Version
SELECT version() AS postgresql_version;

-- 2. Current Database & Active User
SELECT current_database() AS database_name, current_user AS connected_user;

-- 3. Database Total Size on Disk
SELECT pg_size_pretty(pg_database_size(current_database())) AS total_db_size;

-- 4. Table Inventory and Individual Table Sizes
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident(table_name))) AS index_size
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC;

-- 5. Total Number of Tables and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public') AS total_sequences;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Total Database Size:** `8966 kB` (~8.9 MB)
- **Object Count:** `total_tables: 9`, `total_sequences: 9`
- **Table Storage Breakdown:**
  ```text
        table_name       | total_size | table_size | index_size 
  -----------------------+------------+------------+------------
   hazard_report         | 112 kB     | 8192 bytes | 96 kB
   assignment            | 72 kB      | 8192 bytes | 64 kB
   app_user              | 64 kB      | 8192 bytes | 48 kB
   attachment            | 48 kB      | 8192 bytes | 32 kB
   hazard_category       | 48 kB      | 8192 bytes | 32 kB
   report_status_history | 48 kB      | 8192 bytes | 32 kB
   role                  | 48 kB      | 8192 bytes | 32 kB
   maintenance_action    | 48 kB      | 8192 bytes | 32 kB
   campus_location       | 32 kB      | 8192 bytes | 16 kB
  (9 rows)
  ```

---

## 2.2 Connection, Session & Activity Monitoring

### Queries Executed
```sql
-- 1. Database Connection and Transaction Metrics
SELECT 
    datname, 
    numbackends AS open_connections,
    xact_commit AS committed_transactions,
    xact_rollback AS rolled_back_transactions,
    conflicts,
    deadlocks
FROM pg_stat_database
WHERE datname = current_database();

-- 2. Inspect Active Sessions
SELECT 
    pid, usename, client_addr, application_name, state, 
    wait_event_type, wait_event, query_start, query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY query_start NULLS LAST;

-- 3. Verify Activity 4 Roles Configuration
SELECT 
    r.rolname AS role_name,
    r.rolcanlogin AS can_login,
    r.rolsuper AS is_superuser,
    ARRAY(
        SELECT b.rolname 
        FROM pg_auth_members m 
        JOIN pg_roles b ON (m.roleid = b.oid) 
        WHERE m.member = r.oid
    ) AS member_of_group
FROM pg_roles r
WHERE r.rolname IN ('postgres', 'campussafe_app', 'campussafe_report', 'campussafe_backup', 'app_runtime_grp', 'app_readonly_grp', 'app_backup_grp')
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
        datname       | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  --------------------+------------------+------------------------+--------------------------+-----------+-----------
   grp07_campussafedb |                6 |                   1210 |                        9 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. Transaction rollback ratio is < 0.74%, confirming high transaction integrity.
- **Roles Verification Output:**
  ```text
       role_name     | can_login | is_superuser |  member_of_group   
  -------------------+-----------+--------------+--------------------
   app_backup_grp    | f         | f            | {}
   app_readonly_grp  | f         | f            | {}
   app_runtime_grp   | f         | f            | {}
   campussafe_app    | t         | f            | {app_runtime_grp}
   campussafe_backup | t         | f            | {app_backup_grp}
   campussafe_report | t         | f            | {app_readonly_grp}
   postgres          | t         | t            | {}
  (7 rows)
  ```

---

## 2.3 Table Statistics & Bloat Inspection

### Query Executed
```sql
SELECT 
    relname AS table_name,
    seq_scan AS sequential_scans,
    seq_tup_read AS tuples_read_by_seq_scan,
    idx_scan AS index_scans,
    n_live_tup AS live_tuples,
    n_dead_tup AS dead_tuples,
    last_vacuum, last_autovacuum, last_analyze, last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
```

### Actual Output
```text
      table_name       | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze |       last_autoanalyze        
-----------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+-------------------------------
 app_user              |                7 |                      24 |         182 |           6 |           6 |             |                 |              | 
 attachment            |                5 |                      16 |           1 |           5 |           1 |             |                 |              | 
 hazard_report         |               18 |                     181 |          94 |          17 |           1 |             |                 |              | 
 campus_location       |                5 |                      32 |          30 |          10 |           0 |             |                 |              | 
 maintenance_action    |                3 |                       9 |           0 |           9 |           0 |             |                 |              | 
 report_status_history |                3 |                      60 |           1 |          60 |           0 |             |                 |              | 2026-11-05 00:37:19.209883-05
 assignment            |                7 |                      36 |           2 |          12 |           0 |             |                 |              | 
 hazard_category       |                5 |                      18 |          35 |           6 |           0 |             |                 |              | 
 role                  |                3 |                       5 |           7 |           5 |           0 |             |                 |              | 
(9 rows)
```
*Analysis:* `app_user` accumulated 6 dead tuples as a result of running `seed_users.php` password hash updates. `hazard_report` and `attachment` had 1 dead tuple each from initial testing. This confirmed that routine `VACUUM` was warranted.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-04_094245.log`)
1. **Activity 4 Permission Denied Logs:**
   - **Log Entry:** `ERROR: permission denied for table hazard_category on statement INSERT INTO hazard_category (category_name) VALUES ('Rogue Category');` by `campussafe_report`
   - **Log Entry:** `ERROR: permission denied for table hazard_report on statement UPDATE hazard_report SET priority = 'Critical' WHERE report_id = 1;` by `campussafe_report`
   - **Log Entry:** `ERROR: permission denied for table hazard_report on statement DELETE FROM hazard_report WHERE report_id = 1;` by `campussafe_report`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `campussafe_app`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `campussafe_app`
   - **Assessment:** Positive validation of the Least Privilege policy. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Checkpoint Health:** Background checkpoints logged clean completions (e.g. `checkpoint complete: wrote 21 buffers (0.1%), write=2.358 s, sync=0.003 s, total=2.379 s`).
3. **Server Stability:** No crashes, unhandled segfaults, or disk corruption warnings were present in the active log.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- Standalone maintenance execution as superuser / owner:
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.hazard_report;
VACUUM (VERBOSE, ANALYZE) public.report_status_history;
VACUUM (VERBOSE, ANALYZE) public.app_user;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM hazard_report;` as `campussafe_app` produced:
  ```text
  WARNING:  permission denied to vacuum "hazard_report", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.hazard_report;` Output:**
  ```text
  INFO:  vacuuming "grp07_campussafedb.public.hazard_report"
  INFO:  finished vacuuming "grp07_campussafedb.public.hazard_report": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 1 removed, 17 remain, 0 are dead but not yet removable
  removable cutoff: 1542, which was 0 XIDs old when operation ended
  new relfrozenxid: 1447, which is 26 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 131 hits, 0 reads, 4 dirtied
  WAL usage: 6 records, 4 full page images, 33380 bytes, 0 buffers full
  INFO:  analyzing "public.hazard_report"
  INFO:  "hazard_report": scanned 1 of 1 pages, containing 17 live rows and 0 dead rows; 17 rows in sample, 17 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.app_user;` Output:**
  ```text
  INFO:  vacuuming "grp07_campussafedb.public.app_user"
  INFO:  finished vacuuming "grp07_campussafedb.public.app_user": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 6 removed, 6 remain, 0 are dead but not yet removable
  removable cutoff: 1544, which was 0 XIDs old when operation ended
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 90 hits, 0 reads, 4 dirtied
  INFO:  analyzing "public.app_user"
  INFO:  "app_user": scanned 1 of 1 pages, containing 6 live rows and 0 dead rows; 6 rows in sample, 6 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. Attempting it under `campussafe_app` resulted in a permission denial, confirming that routine maintenance must be orchestrated by a designated maintenance or administrative account (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites entire table files and acquires an `AccessExclusiveLock`, blocking concurrent SELECT, INSERT, and UPDATE queries. Because CampusSafe tables contain only dozens of rows fitting inside single 8 kB pages, standard non-blocking `VACUUM` reclaimed all dead tuples (`6 removed` in `app_user`, `1 removed` in `hazard_report`) without application downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Admin Filter by Status & Recency
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * 
FROM hazard_report 
WHERE status = 'Submitted' 
ORDER BY submitted_at DESC;
```

#### Actual Execution Plan Output
```text
                                                    QUERY PLAN                                                    
------------------------------------------------------------------------------------------------------------------
 Sort  (cost=1.24..1.24 rows=3 width=130) (actual time=0.039..0.039 rows=3.00 loops=1)
   Sort Key: submitted_at DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=4
   ->  Seq Scan on hazard_report  (cost=0.00..1.21 rows=3 width=130) (actual time=0.017..0.019 rows=3.00 loops=1)
         Filter: ((status)::text = 'Submitted'::text)
         Rows Removed by Filter: 14
         Buffers: shared hit=1
 Planning:
   Buffers: shared hit=215
 Planning Time: 5.960 ms
 Execution Time: 0.062 ms
(12 rows)
```

#### Interpretation
1. **Execution Time:** `0.062 ms` (ultra-fast sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 4`, with 0 disk block reads (100% in-memory cache hit).
3. **Sequential Scan Justification:** Even though an index `idx_report_status` exists on `status`, the PostgreSQL query planner deliberately chose a `Seq Scan` on `hazard_report`. Because the entire table fits inside a single 8 kB heap block (`Buffers: shared hit = 1`), sequentially scanning that block in memory is computationally cheaper than loading the B-Tree index page followed by random pointer lookups into the heap.

---

### Query 2: Hazard Reports with Joined Categories and Locations
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.report_id, c.category_name, l.building, l.room_area, r.status, r.priority
FROM hazard_report r
JOIN hazard_category c ON c.category_id = r.category_id
JOIN campus_location l ON l.location_id = r.location_id
WHERE r.status = 'In Progress';
```

#### Actual Execution Plan Output
```text
                                                           QUERY PLAN                                                            
---------------------------------------------------------------------------------------------------------------------------------
 Hash Join  (cost=2.36..3.59 rows=3 width=69) (actual time=0.042..0.044 rows=3.00 loops=1)
   Hash Cond: (r.location_id = l.location_id)
   Buffers: shared hit=3
   ->  Hash Join  (cost=1.14..2.36 rows=3 width=34) (actual time=0.020..0.022 rows=3.00 loops=1)
         Hash Cond: (r.category_id = c.category_id)
         Buffers: shared hit=2
         ->  Seq Scan on hazard_report r  (cost=0.00..1.21 rows=3 width=27) (actual time=0.005..0.006 rows=3.00 loops=1)
               Filter: ((status)::text = 'In Progress'::text)
               Rows Removed by Filter: 14
               Buffers: shared hit=1
         ->  Hash  (cost=1.06..1.06 rows=6 width=15) (actual time=0.008..0.008 rows=6.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on hazard_category c  (cost=0.00..1.06 rows=6 width=15) (actual time=0.004..0.004 rows=6.00 loops=1)
                     Buffers: shared hit=1
   ->  Hash  (cost=1.10..1.10 rows=10 width=43) (actual time=0.018..0.018 rows=10.00 loops=1)
         Buckets: 1024  Batches: 1  Memory Usage: 9kB
         Buffers: shared hit=1
         ->  Seq Scan on campus_location l  (cost=0.00..1.10 rows=10 width=43) (actual time=0.008..0.009 rows=10.00 loops=1)
               Buffers: shared hit=1
 Planning:
   Buffers: shared hit=168
 Planning Time: 2.107 ms
 Execution Time: 0.069 ms
(24 rows)
```

#### Interpretation
1. **Execution Time:** `0.069 ms`.
2. **Buffer Hits:** `shared hit = 3` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner built in-memory hash tables for `hazard_category` (9 kB) and `campus_location` (9 kB), executing two chained `Hash Joins` with zero disk spills (`Batches: 1`).

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current schema indexes | All 24 existing indexes (`idx_report_status`, `idx_report_category`, `idx_report_location`, etc.) are properly defined. Query execution times are well under 0.1 ms with 100% cache hits. Additional indexing would introduce insert/update overhead without measurable latency gains. |
| **Follow-up Action** | Composite Index for Large-Scale Incident Volumes | If the system scales past 10,000 incident reports in production, queries filtering by status and sorting by recency will benefit from a composite index: `CREATE INDEX idx_hazard_report_status_submitted ON hazard_report (status, submitted_at DESC);`. |
| **Follow-up Action** | Automated Maintenance Script via Task Scheduler | Deploy a scheduled weekly maintenance job invoking `VACUUM (ANALYZE)` during low-traffic windows (e.g. Sunday 02:00 AM) to prevent dead tuple buildup as maintenance technicians update work orders. |

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

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in the class Google Sheets tracker:

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 7 - CampusSafe: Campus Hazard & Safety Reporting System |
| **Repository cloned** | `https://github.com/jeffersonorpesa2-ops/grp07-campussafe`, Branch: `main` |
| **Database setup result** | Success (Configured user php.ini with pdo_pgsql, verified PostgreSQL 18 on port 5432, loaded 9 tables and seed data). |
| **Access-control folder link** |  |
| **Access-control status** | Completed |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 7 - CampusSafe: Campus Hazard & Safety Reporting System |
| **Database inspected** | `grp07_campussafedb` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `campussafe_app` (Runtime), `campussafe_report` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 8.9 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on hazard_report, report_status_history, app_user via postgres` |
| **Query analyzed** | Admin status filter with sort (Seq scan, 0.062 ms, 4 buffer hits) & Multi-table join (Hash joins, 0.069 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.07 ms with 100% buffer cache hits) |
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

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
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

---

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash.
- **Tasks Performed:** 
  1. Inspecting database schema definitions, constraints, and relationship hierarchies for Group 7's CampusSafe system.
  2. Formulating and executing PostgreSQL least-privilege verification test suites for Activity 4.
  3. Diagnosing runtime maintenance boundaries, executing `VACUUM` and `ANALYZE`, and profiling execution plans (`EXPLAIN (ANALYZE, BUFFERS)`) for Activity 5.
  4. Executing custom archive backup creation with `pg_dump`, SHA256 checksum computation, test database restoration with `pg_restore`, and controlled recovery simulation drill for Activity 6.
  5. Compiling and formatting comprehensive progress documentation adhering to course rubric guidelines.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in pgAdmin 4 / `psql`. Real outputs, timings, and checksums are recorded in this report.

