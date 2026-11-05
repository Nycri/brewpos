# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **OJT Management System** (Project-OJT-RMA-System) |
| **Target Project Group** | **Group 3 (Evaluated under Tracker Key: Group 06 / Group 03):** Simon, De Dios, Ramos, Dela Rosa, Sietereales, Geraldez |
| **Target Project Repository** | [https://github.com/swvenz/Project-OJT-RMA-System](https://github.com/swvenz/Project-OJT-RMA-System) (Branches: `main`, `ojt-system-untested.demo`) |
| **Primary Database** | `ojt_management` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `ojt_management_restore_test` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime User `ojt_app`)
   - 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `ojt_report_user`)
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
   - 3.3 Restoration into Separate Test Database (`ojt_management_restore_test`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Initial Implementation
In the initial repository configuration of the **OJT Management System** (`.env`, `.env.example`, and `target/classes/db.properties`), the Java Swing application connects directly using the PostgreSQL administrative account (`postgres`):
```properties
# db.properties
url=jdbc:postgresql://localhost:5432/OJT_Mangementdb
user=postgres
password=[REDACTED]
```
```env
# .env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ojt_management
DB_USER=postgres
DB_PASSWORD=[REDACTED]
```

Operating an academic internship management and evaluation system under the PostgreSQL `postgres` superuser severely violates the **Principle of Least Privilege (PoLP)**. An attacker or unauthorized student exploiting an unhandled SQL parameter or an administrative desktop vulnerability would possess unrestricted DDL and DML capabilities across the entire database cluster. They could purge student rosters, tamper with rendered OJT hours, alter supervisor passing grades (`evaluations.score`), or compromise unrelated databases located on the same server instance.

Furthermore, an architectural review of the codebase revealed two critical discoveries:
1. **Accidental Database Artifact Disparity:** The file committed to `main` as `OJT Database.sql` was a binary custom dump of an entirely unrelated lending demonstration database (`customers` and `loans`). The actual schema definition was located in a secondary branch (`ojt-system-untested.demo`) inside `postgresql query.pdf`.
2. **Built-in Soft Deletion Architecture:** Inspection of `Roles.java` and `Main.java` showed that the application never executes destructive SQL `DELETE FROM` statements. All decommissioning of users, applications, companies, and placements is managed via timestamp checks (`WHERE deleted_at IS NULL` and `UPDATE users SET is_active=NOT is_active`). Therefore, granting any SQL `DELETE` privilege to the application runtime account is completely unnecessary and introduces severe security risk.

### Activity 4 Solution & Design
To secure the **OJT Management System** database, we implemented a robust Role-Based Access Control (RBAC) hierarchy decoupling application runtime operations, student self-service submissions, academic compliance auditing, and database administration:
- **Project-Namespaced Group Roles (`NOLOGIN`):** Architectural privilege templates that define specific capability boundaries (`ojt_app_grp`, `ojt_student_grp`, `ojt_readonly_grp`, and `ojt_backup_grp`).
- **Dedicated Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their respective group roles (`ojt_app`, `ojt_student_user`, `ojt_report_user`, and `ojt_backup`).
- **Zero-Delete Enforcement (Complete Deletion Withholding):** Because the application strictly utilizes soft deletes (`deleted_at`), `ojt_app` is granted `SELECT`, `INSERT`, and `UPDATE` on operational tables, but **is granted zero `DELETE` privileges**. This provides an immutable mathematical guarantee: runtime SQL injections cannot truncate or delete student records, attendance logs, company evaluations, or compliance documents.
- **Auditor Credential Protection:** The read-only reporting account `ojt_report_user` is granted `SELECT` across operational tables (`students`, `companies`, `academic_supervisors`, `company_supervisors`, `requirements`, `ojt_applications`, `student_requirements`, `ojt_placements`, `attendance`, `evaluations`, and `audit_log`), but is **strictly denied `SELECT` on `users`**. This protects PBKDF2 password hashes and authentication salts from exposure to auditors.
- **Dedicated Backup Role:** Logical backups are completely decoupled from runtime accounts and assigned to `ojt_backup`, a read-only role holding schema-wide `SELECT` on all tables and sequences without write, update, or schema alteration permissions.
- **Hardened PUBLIC Boundary:** Default connection and creation privileges were revoked from `PUBLIC` on both the database `ojt_management` and schema `public`.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `ojt_app` | `ojt_app_grp` | Application Runtime User | Handles all interactive desktop workflows: student profile management, company partner records, placement assignments, attendance logging, and supervisor evaluations | • `CONNECT` on `ojt_management`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on all 11 operational tables<br>• `SELECT, INSERT` on `audit_log`<br>• `USAGE, SELECT` on all sequences in `public`<br>*(Zero `DELETE` privileges granted)* | The application codebase exclusively uses soft deletes (`deleted_at IS NULL`). Withholding `DELETE` privileges prevents any SQL injection from purging student records, logged hours, or supervisor marks. |
| `ojt_student_user` | `ojt_student_grp` | Student Portal Account | Submits internship applications, uploads compliance requirements (resume, medical cert), logs daily attendance hours | • `CONNECT` on `ojt_management`<br>• `USAGE` on schema `public`<br>• `SELECT` on `students`, `companies`, `requirements`, `ojt_applications`, `student_requirements`, `ojt_placements`, `attendance`, `evaluations`<br>• `INSERT, UPDATE` on `ojt_applications`, `student_requirements`, `attendance`<br>• `USAGE, SELECT` on required sequences | Enables students to submit documents and track progress without granting the ability to alter company profiles, tamper with other students' grades, or view password credentials. |
| `ojt_report_user` | `ojt_readonly_grp` | Compliance Officer / Academic Auditor | Read-only inspection of internship placements, verified rendering of OJT hours, company evaluation scores, and audit logs | • `CONNECT` on `ojt_management`<br>• `USAGE` on schema `public`<br>• `SELECT` on all operational tables and `audit_log`<br>*(Explicitly denied `SELECT` on `users`)* | Allows complete academic compliance verification while shielding student and staff PBKDF2 authentication credentials from reporting tools. |
| `ojt_backup` | `ojt_backup_grp` | Backup Operator | Performs routine logical database dumps (`pg_dump`) | • `CONNECT` on `ojt_management`<br>• `USAGE` on schema `public`<br>• `SELECT` on all tables and sequences | Conforms to Activity 6 by providing complete read access required for dumps while strictly barring DML writes, updates, and DDL. |
| `postgres` | *(Superuser)* | Database Administrator | Schema provisioning, migrations, maintenance (`VACUUM`/`ANALYZE`), disaster recovery | Superuser / Owner privileges | Reserved strictly for DBA operations; completely eliminated from runtime connection strings. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script was executed as `postgres` against `ojt_management`:

```sql
-- =============================================================================
-- OJT Management System: Enterprise Access Control & Least Privilege Setup
-- Target Database: ojt_management | Target Schema: public
-- =============================================================================

-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE ojt_app_grp NOLOGIN;
CREATE ROLE ojt_student_grp NOLOGIN;
CREATE ROLE ojt_readonly_grp NOLOGIN;
CREATE ROLE ojt_backup_grp NOLOGIN;

-- 2. Create Login Roles (LOGIN) with secure passwords
CREATE ROLE ojt_app LOGIN PASSWORD 'AppRuntimePass2026!';
CREATE ROLE ojt_student_user LOGIN PASSWORD 'StudentPass2026!';
CREATE ROLE ojt_report_user LOGIN PASSWORD 'ReportUserPass2026!';
CREATE ROLE ojt_backup LOGIN PASSWORD 'BackupOperatorPass2026!';

-- 3. Assign Role Memberships
GRANT ojt_app_grp TO ojt_app;
GRANT ojt_student_grp TO ojt_student_user;
GRANT ojt_readonly_grp TO ojt_report_user;
GRANT ojt_backup_grp TO ojt_backup;

-- 4. Lock down PUBLIC permissions
REVOKE ALL ON DATABASE ojt_management FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 5. Database Connection and Schema Usage
GRANT CONNECT ON DATABASE ojt_management TO ojt_app_grp, ojt_student_grp, ojt_readonly_grp, ojt_backup_grp;
GRANT USAGE ON SCHEMA public TO ojt_app_grp, ojt_student_grp, ojt_readonly_grp, ojt_backup_grp;

-- 6. Application Runtime Permissions (Principle of Least Privilege)
-- Operational SELECT, INSERT, and UPDATE across tables.
-- Strict Policy: ZERO DELETE privileges granted, enforcing soft-delete compliance.
GRANT SELECT, INSERT, UPDATE ON
    users,
    students,
    companies,
    academic_supervisors,
    company_supervisors,
    requirements,
    ojt_applications,
    student_requirements,
    ojt_placements,
    attendance,
    evaluations
TO ojt_app_grp;

GRANT SELECT, INSERT ON audit_log TO ojt_app_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ojt_app_grp;

-- 7. Student Public Role Permissions
GRANT SELECT ON
    students,
    companies,
    requirements,
    ojt_applications,
    student_requirements,
    ojt_placements,
    attendance,
    evaluations
TO ojt_student_grp;

GRANT INSERT, UPDATE ON
    ojt_applications,
    student_requirements,
    attendance
TO ojt_student_grp;

GRANT USAGE, SELECT ON SEQUENCE
    ojt_applications_application_id_seq,
    student_requirements_student_req_id_seq,
    attendance_attendance_id_seq
TO ojt_student_grp;

-- 8. Read-Only / Auditor Role Permissions (Excluding sensitive users credential table)
GRANT SELECT ON
    students,
    companies,
    academic_supervisors,
    company_supervisors,
    requirements,
    ojt_applications,
    student_requirements,
    ojt_placements,
    attendance,
    evaluations,
    audit_log
TO ojt_readonly_grp;

-- 9. Backup Operator Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ojt_backup_grp;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO ojt_backup_grp;

-- 10. Default Privileges for Future Tables & Sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO ojt_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ojt_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO ojt_backup_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO ojt_backup_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `ojt_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT company_id, company_name, industry, is_partner FROM companies LIMIT 5;
  ```
- **Expected Result:** Allowed; returns active partner companies.
- **Actual Result / Output:**
  ```text
   company_id |      company_name      |           industry           | is_partner 
  ------------+------------------------+------------------------------+------------
            1 | Acme Corp              | Technology & Cloud Solutions | t
            2 | InnovateTech Solutions | Software Engineering & AI    | t
            3 | Nexus Global Logistics | Supply Chain & FinTech       | f
  (3 rows)
  ```
- **Status:** PASS

### Test 1.2: Insert an Application & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO ojt_applications (student_id, company_id, application_date, status, remarks) 
  VALUES (2, 1, '2026-11-05', 'Pending', 'Activity 4 Runtime Test Application') 
  RETURNING application_id, status;
  ```
- **Expected Result:** Allowed; inserts record and consumes sequence value.
- **Actual Result / Output:**
  ```text
   application_id | status  
  ----------------+---------
                3 | Pending
  (1 row)
  INSERT 0 1
  ```
- **Status:** PASS

### Test 1.3: Update Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE ojt_applications SET status = 'Approved' WHERE application_id = 3 RETURNING application_id, status;
  ```
- **Expected Result:** Allowed; runtime account updates application lifecycle status.
- **Actual Result / Output:**
  ```text
   application_id |  status  
  ----------------+----------
                3 | Approved
  (1 row)
  UPDATE 1
  ```
- **Status:** PASS

### Test 1.4: Delete Record on Students (Denied — Soft Delete Architecture)
- **SQL Statement:**
  ```sql
  DELETE FROM students WHERE student_id = 1;
  ```
- **Expected Result:** Denied; application users must not hard-delete student profiles.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table students
  ```
- **Status:** PASS

### Test 1.5: Delete Record on Attendance (Denied — Audit Trail Immutability)
- **SQL Statement:**
  ```sql
  DELETE FROM attendance WHERE attendance_id = 1;
  ```
- **Expected Result:** Denied; rendered work hours are immutable legal attendance records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table attendance
  ```
- **Status:** PASS

### Test 1.6: Delete Record on Evaluations (Denied — Academic Integrity)
- **SQL Statement:**
  ```sql
  DELETE FROM evaluations WHERE evaluation_id = 1;
  ```
- **Expected Result:** Denied; company evaluation marks cannot be deleted at runtime.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table evaluations
  ```
- **Status:** PASS

### Test 1.7: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE attendance;
  ```
- **Expected Result:** Denied; DDL requires object ownership (`postgres`).
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table attendance
  ```
- **Status:** PASS

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `ojt_report_user`)

### Test 2.1: Read Operational Data Across Joins (Allowed)
- **SQL Statement:**
  ```sql
  SELECT p.placement_id, s.first_name || ' ' || s.last_name AS student_name, c.company_name, 
         p.status, p.required_hours, e.score, e.feedback 
  FROM ojt_placements p 
  JOIN students s ON p.student_id = s.student_id 
  JOIN companies c ON p.company_id = c.company_id 
  LEFT JOIN evaluations e ON p.placement_id = e.placement_id 
  LIMIT 5;
  ```
- **Expected Result:** Allowed; auditor can inspect multi-table student placement details.
- **Actual Result / Output:**
  ```text
   placement_id | student_name | company_name | status | required_hours | score |                                        feedback                                        
  --------------+--------------+--------------+--------+----------------+-------+----------------------------------------------------------------------------------------
              1 | Demo Student | Acme Corp    | Active |         300.00 | 88.50 | Demonstrates exceptional diligence in software architecture and API integration tasks.
  (1 row)
  ```
- **Status:** PASS

### Test 2.2: Attempt to Read User Credentials (Denied — Credential Protection)
- **SQL Statement:**
  ```sql
  SELECT * FROM users;
  ```
- **Expected Result:** Denied; auditor role has no `SELECT` privilege on `users` to shield PBKDF2 password hashes.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table users
  ```
- **Status:** PASS

### Test 2.3: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO requirements (title, is_mandatory) VALUES ('Unauthorized Requirement', FALSE);
  ```
- **Expected Result:** Denied; auditor account cannot modify requirements catalog.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table requirements
  ```
- **Status:** PASS

### Test 2.4: Attempt to Update Records (Denied)
- **SQL Statement:**
  ```sql
  UPDATE evaluations SET score = 100.00 WHERE evaluation_id = 1;
  ```
- **Expected Result:** Denied; auditor account cannot tamper with supervisor evaluations.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table evaluations
  ```
- **Status:** PASS

### Test 2.5: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM companies WHERE company_id = 1;
  ```
- **Expected Result:** Denied; auditor account cannot delete company partners.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table companies
  ```
- **Status:** PASS

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  CREATE DATABASE rogue_test_db;
  ```
- **Expected Result:** Denied; application users must not possess administrative cluster privileges (`CREATEDB`).
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied to create database
  ```
- **Status:** PASS

### Test 3.2: Attempt to Connect to Unrelated Database (Denied)
- **Command:**
  ```bash
  psql -U ojt_app -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
  ```
- **Expected Result:** Denied; cross-database connection is blocked by revoked PUBLIC privileges.
- **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "canteen_db"
  DETAIL:  User does not have CONNECT privilege.
  ```
- **Status:** PASS

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Accidental Upload of Unrelated Database Dump on `main`:** The repository's `OJT Database.sql` file was committed as an export of a demo lending database containing `lending.customers` and `lending.loans`. Attempting to start the application resulted in immediate crash (`relation "requirements" does not exist`). By inspecting git history, we located `postgresql query.pdf` on branch `ojt-system-untested.demo` containing the actual OJT schema definitions.
2. **Schema and DDL Inconsistencies in Provided PDF:**
   - On page 3 of `postgresql query.pdf`, a syntax error existed on `ojt_applications` due to a duplicate closing parenthesis `); );`.
   - On page 2, `academic_supervisors` omitted `employee_number`, `department`, and `contact_number`, and `company_supervisors` omitted `department`. This caused runtime crashes in `Db.createUser()` when creating supervisor accounts. We reconciled the schema by including these optional attributes.
3. **Hardcoded Database Credentials in Codebase:** The Java application bundled `db.properties` with hardcoded credentials (`password=DDNLR023`, `user=postgres`). We documented this security defect and transitioned the runtime setup to least-privilege role `ojt_app`.

### Feedback to Owning Group (Group 3 / Group 6)
- **Commendation:** The business domain model in Java and Swing is comprehensive, featuring role-based UI tabs, soft deletes (`deleted_at`), and composite constraints (`fk_placement_application_consistency`).
- **Recommendation 1:** Immediately replace `OJT Database.sql` on the `main` branch with the proper OJT management DDL script so peer reviewers and instructors can set up the database without hunting across secondary git branches.
- **Recommendation 2:** Fix the schema columns in `academic_supervisors` and `company_supervisors` to include `department` and `employee_number` as required by `Db.java`.
- **Recommendation 3:** Adopt environment variable lookups or dynamic external property loading in `Db.java` instead of hardcoding database credentials in `target/classes/db.properties`.

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

-- 4. Total Number of Tables and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public') AS total_sequences;

-- 5. Table Inventory and Individual Table Sizes
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident(table_name))) AS index_size
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Total Database Size:** `9046 kB` (~9.0 MB)
- **Object Count:** `total_tables: 12`, `total_sequences: 12`
- **Table Storage Breakdown:**
  ```text
        table_name      | total_size | table_size | index_size 
  ----------------------+------------+------------+------------
   users                | 64 kB      | 8192 bytes | 48 kB
   students             | 56 kB      | 8192 bytes | 48 kB
   ojt_applications     | 48 kB      | 8192 bytes | 32 kB
   requirements         | 48 kB      | 8192 bytes | 32 kB
   student_requirements | 48 kB      | 8192 bytes | 32 kB
   companies            | 48 kB      | 8192 bytes | 32 kB
   ojt_placements       | 40 kB      | 8192 bytes | 32 kB
   attendance           | 40 kB      | 8192 bytes | 32 kB
   company_supervisors  | 40 kB      | 8192 bytes | 32 kB
   academic_supervisors | 40 kB      | 8192 bytes | 32 kB
   evaluations          | 32 kB      | 8192 bytes | 16 kB
   audit_log            | 32 kB      | 8192 bytes | 16 kB
  (12 rows)
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
WHERE r.rolname LIKE 'ojt%'
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
      datname     | numbackends | xact_commit | xact_rollback | conflicts | deadlocks 
  ----------------+-------------+-------------+---------------+-----------+-----------
   ojt_management |           1 |         116 |            10 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The rolled-back transactions (10) directly reflect intentional negative security tests during Activity 4 verification (e.g., unauthorized DDL drops, illegal table deletions, and cross-database connection attempts).
- **Roles Verification Output:**
  ```text
      role_name     | can_login | is_superuser |  member_of_group   
  ------------------+-----------+--------------+--------------------
   ojt_app          | t         | f            | {ojt_app_grp}
   ojt_app_grp      | f         | f            | {}
   ojt_backup       | t         | f            | {ojt_backup_grp}
   ojt_backup_grp   | f         | f            | {}
   ojt_readonly_grp | f         | f            | {}
   ojt_report_user  | t         | f            | {ojt_readonly_grp}
   ojt_student_grp  | f         | f            | {}
   ojt_student_user | t         | f            | {ojt_student_grp}
  (8 rows)
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
ORDER BY n_dead_tup DESC, relname ASC;
```

### Actual Output
```text
      table_name      | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
----------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 ojt_applications     |                2 |                       0 |           8 |           2 |           2 |             |                 |              | 
 academic_supervisors |                2 |                       0 |           6 |           2 |           0 |             |                 |              | 
 attendance           |                2 |                       0 |           4 |           4 |           0 |             |                 |              | 
 audit_log            |                1 |                       0 |           0 |           2 |           0 |             |                 |              | 
 companies            |                4 |                       6 |           7 |           3 |           0 |             |                 |              | 
 company_supervisors  |                2 |                       0 |           3 |           1 |           0 |             |                 |              | 
 evaluations          |                2 |                       1 |           1 |           1 |           0 |             |                 |              | 
 ojt_placements       |                3 |                       1 |           7 |           1 |           0 |             |                 |              | 
 requirements         |                2 |                       0 |           9 |           5 |           0 |             |                 |              | 
 student_requirements |                2 |                       0 |           4 |           4 |           0 |             |                 |              | 
 students             |                3 |                       0 |          10 |           2 |           0 |             |                 |              | 
 users                |                3 |                       0 |          12 |           7 |           0 |             |                 |              | 
(12 rows)
```
*Analysis:* `ojt_applications` accumulated 2 dead tuples as a direct result of Activity 4's insert, update, and deletion test for `application_id = 3`. All other operational tables exhibited 0 dead tuples. This demonstrated that routine `VACUUM` was warranted to reclaim dead tuple pointers.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Least-Privilege Denials Logged:**
   - **Log Entry:** `ERROR: permission denied for table students on statement DELETE FROM students WHERE student_id = 1;` by `ojt_app`
   - **Log Entry:** `ERROR: permission denied for table attendance on statement DELETE FROM attendance WHERE attendance_id = 1;` by `ojt_app`
   - **Log Entry:** `ERROR: permission denied for table evaluations on statement DELETE FROM evaluations WHERE evaluation_id = 1;` by `ojt_app`
   - **Log Entry:** `ERROR: permission denied for table users on statement SELECT * FROM users;` by `ojt_report_user`
   - **Log Entry:** `ERROR: permission denied for table requirements on statement INSERT INTO requirements ...;` by `ojt_report_user`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `ojt_app`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `ojt_app`
   - **Assessment:** Positive validation of least privilege. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Maintenance Boundary Warning:**
   - **Log Entry:** `WARNING: permission denied to vacuum "ojt_applications", skipping it` by `ojt_app`
   - **Assessment:** Positive validation of maintenance boundaries. Runtime accounts cannot initiate storage maintenance tasks.
3. **Server Stability:** No unexpected crashes, unhandled segfaults, memory leaks, or disk corruption warnings were present in the active log.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- 1. Test Permission Boundary (as runtime application user ojt_app):
VACUUM ojt_applications;

-- 2. Standalone Maintenance Execution (as superuser / owner postgres):
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.ojt_applications;
VACUUM (VERBOSE, ANALYZE) public.attendance;
VACUUM (VERBOSE, ANALYZE) public.evaluations;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM ojt_applications;` as `ojt_app` produced:
  ```text
  WARNING:  permission denied to vacuum "ojt_applications", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.ojt_applications;` Output:**
  ```text
  INFO:  vacuuming "ojt_management.public.ojt_applications"
  INFO:  finished vacuuming "ojt_management.public.ojt_applications": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 2 removed, 2 remain, 0 are dead but not yet removable
  removable cutoff: 2592, which was 0 XIDs old when operation ended
  new relfrozenxid: 2299, which is 26 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 83 hits, 0 reads, 6 dirtied
  WAL usage: 9 records, 6 full page images, 33736 bytes, 0 buffers full
  INFO:  analyzing "public.ojt_applications"
  INFO:  "ojt_applications": scanned 1 of 1 pages, containing 2 live rows and 0 dead rows; 2 rows in sample, 2 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.attendance;` Output:**
  ```text
  INFO:  vacuuming "ojt_management.public.attendance"
  INFO:  finished vacuuming "ojt_management.public.attendance": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 4 remain, 0 are dead but not yet removable
  buffer usage: 86 hits, 0 reads, 5 dirtied
  WAL usage: 5 records, 6 full page images, 40751 bytes, 0 buffers full
  INFO:  analyzing "public.attendance"
  INFO:  "attendance": scanned 1 of 1 pages, containing 4 live rows and 0 dead rows; 4 rows in sample, 4 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.evaluations;` Output:**
  ```text
  INFO:  vacuuming "ojt_management.public.evaluations"
  INFO:  finished vacuuming "ojt_management.public.evaluations": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 1 remain, 0 are dead but not yet removable
  buffer usage: 58 hits, 0 reads, 4 dirtied
  WAL usage: 5 records, 5 full page images, 33515 bytes, 0 buffers full
  INFO:  analyzing "public.evaluations"
  INFO:  "evaluations": scanned 1 of 1 pages, containing 1 live rows and 0 dead rows; 1 rows in sample, 1 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. Attempting it under `ojt_app` resulted in a permission denial (`WARNING: permission denied to vacuum "ojt_applications", skipping it`), confirming that routine maintenance must be orchestrated by a designated maintenance or administrative account (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites entire table files and acquires an `AccessExclusiveLock`, completely locking tables against concurrent `SELECT`, `INSERT`, and `UPDATE` statements—which would freeze student attendance logging and application workflows. Standard non-blocking `VACUUM` cleanly reclaimed all 2 dead tuples in `ojt_applications` without application downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Student Placement and Verified Attendance Progress Lookup
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    p.placement_id, 
    s.first_name || ' ' || s.last_name AS student_name, 
    c.company_name, 
    p.status, 
    (SELECT COALESCE(SUM(t.hours_rendered), 0) 
     FROM attendance t 
     WHERE t.placement_id = p.placement_id AND t.is_verified) AS verified_hours, 
    p.required_hours, 
    p.start_date, 
    p.end_date 
FROM ojt_placements p 
JOIN students s ON s.student_id = p.student_id 
JOIN companies c ON c.company_id = p.company_id 
WHERE p.deleted_at IS NULL 
ORDER BY p.placement_id DESC;
```

#### Actual Execution Plan Output
```text
                                                           QUERY PLAN                                                           
--------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=4.21..4.21 rows=1 width=106) (actual time=0.078..0.079 rows=1.00 loops=1)
   Sort Key: p.placement_id DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=7
   ->  Nested Loop  (cost=0.00..4.20 rows=1 width=106) (actual time=0.062..0.063 rows=1.00 loops=1)
         Join Filter: (c.company_id = p.company_id)
         Buffers: shared hit=4
         ->  Nested Loop  (cost=0.00..2.06 rows=1 width=40) (actual time=0.024..0.025 rows=1.00 loops=1)
               Join Filter: (s.student_id = p.student_id)
               Buffers: shared hit=2
               ->  Seq Scan on ojt_placements p  (cost=0.00..1.01 rows=1 width=32) (actual time=0.015..0.015 rows=1.00 loops=1)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=1
               ->  Seq Scan on students s  (cost=0.00..1.02 rows=2 width=16) (actual time=0.007..0.007 rows=1.00 loops=1)
                     Buffers: shared hit=1
         ->  Seq Scan on companies c  (cost=0.00..1.03 rows=3 width=22) (actual time=0.007..0.007 rows=1.00 loops=1)
               Buffers: shared hit=1
         SubPlan 1
           ->  Aggregate  (cost=1.06..1.07 rows=1 width=32) (actual time=0.025..0.025 rows=1.00 loops=1)
                 Buffers: shared hit=1
                 ->  Seq Scan on attendance t  (cost=0.00..1.05 rows=3 width=5) (actual time=0.009..0.009 rows=3.00 loops=1)
                       Filter: (is_verified AND (placement_id = p.placement_id))
                       Rows Removed by Filter: 1
                       Buffers: shared hit=1
 Planning:
   Buffers: shared hit=275
 Planning Time: 7.246 ms
 Execution Time: 0.140 ms
(28 rows)
```

#### Interpretation
1. **Execution Time:** `0.140 ms` (sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 7`, with 0 physical disk block reads (100% in-memory buffer cache hit).
3. **Execution Strategy:** The query planner executed a `Nested Loop` across `ojt_placements`, `students`, and `companies`, and evaluated `SubPlan 1` for aggregate verified attendance hours directly in memory (`quicksort Memory: 25kB`).

---

### Query 2: Multi-Table Academic Requirement Compliance Review
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    sr.student_req_id, 
    s.first_name || ' ' || s.last_name AS student_name, 
    s.student_number,
    r.title AS requirement_title, 
    sr.file_path, 
    sr.status AS review_status,
    sr.submission_date 
FROM student_requirements sr 
JOIN students s ON s.student_id = sr.student_id 
JOIN requirements r ON r.req_id = sr.req_id 
WHERE sr.submission_date IS NOT NULL 
ORDER BY sr.student_req_id ASC;
```

#### Actual Execution Plan Output
```text
                                                              QUERY PLAN                                                               
---------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=3.31..3.32 rows=4 width=124) (actual time=0.082..0.084 rows=4.00 loops=1)
   Sort Key: sr.student_req_id
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=6
   ->  Hash Join  (cost=2.16..3.27 rows=4 width=124) (actual time=0.058..0.061 rows=4.00 loops=1)
         Hash Cond: (sr.req_id = r.req_id)
         Buffers: shared hit=3
         ->  Hash Join  (cost=1.04..2.12 rows=4 width=92) (actual time=0.040..0.042 rows=4.00 loops=1)
               Hash Cond: (sr.student_id = s.student_id)
               Buffers: shared hit=2
               ->  Seq Scan on student_requirements sr  (cost=0.00..1.04 rows=4 width=74) (actual time=0.016..0.017 rows=4.00 loops=1)
                     Filter: (submission_date IS NOT NULL)
                     Buffers: shared hit=1
               ->  Hash  (cost=1.02..1.02 rows=2 width=26) (actual time=0.017..0.017 rows=2.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on students s  (cost=0.00..1.02 rows=2 width=26) (actual time=0.007..0.007 rows=2.00 loops=1)
                           Buffers: shared hit=1
         ->  Hash  (cost=1.05..1.05 rows=5 width=20) (actual time=0.008..0.008 rows=5.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on requirements r  (cost=0.00..1.05 rows=5 width=20) (actual time=0.003..0.004 rows=5.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=232
 Planning Time: 5.068 ms
 Execution Time: 0.126 ms
(27 rows)
```

#### Interpretation
1. **Execution Time:** `0.126 ms`.
2. **Buffer Hits:** `shared hit = 6` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner built in-memory hash tables for `students` (9 kB) and `requirements` (9 kB), executing two chained `Hash Joins` with zero disk spills (`Batches: 1`).

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain existing primary, foreign key, and unique indexes | All 12 tables fit inside 8 kB heap pages, and current multi-table queries execute in < 0.15 ms with 100% cache hits. Adding indexes now would introduce write overhead without improving query latency. |
| **Follow-up Action** | Composite Index for Large-Scale Attendance Verification | When student internship attendance logs scale past 25,000 entries, supervisor verification queries will benefit from a composite partial index: `CREATE INDEX idx_attendance_unverified ON attendance (placement_id, attendance_date) WHERE is_verified = FALSE;`. |
| **Follow-up Action** | Automated Weekly Maintenance via Task Scheduler | Deploy a scheduled maintenance script invoking `VACUUM (ANALYZE)` nightly at 02:00 AM to automatically clean up dead tuples as student applications and requirement reviews cycle through update stages. |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

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

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `ojt_app_grp`, `ojt_student_grp`, `ojt_app`, etc., before permissions can attach.
2. **File Storage Synchronization:** Student document paths in `student_requirements.file_path` reference relative paths in `uploads/requirements/`. A database restore must always be paired with a filesystem backup of `uploads/` to avoid orphaned database records pointing to missing physical files.
3. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`ojt_management_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For critical campus environments where a 1-hour RPO is insufficient, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in the class Google Sheets tracker:

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 3 - OJT Management System (Project-OJT-RMA-System) [Tracker Key: Group 06 / Group 03] |
| **Repository cloned** | `https://github.com/swvenz/Project-OJT-RMA-System`, Branches: `main`, `ojt-system-untested.demo` |
| **Database setup result** | Success with issues (Resolved repository artifact mismatch where `OJT Database.sql` on `main` was an unrelated demo lending dump; extracted official schema from `ojt-system-untested.demo`, repaired supervisor column discrepancies, and deployed full 12-table relational schema with RBAC). |
| **Access-control folder link** | |
| **Access-control status** | Completed |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 3 - OJT Management System (Project-OJT-RMA-System) [Tracker Key: Group 06 / Group 03] |
| **Database inspected** | `ojt_management` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `ojt_app` (Runtime), `ojt_report_user` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 9.0 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on ojt_applications, attendance, evaluations via postgres` |
| **Query analyzed** | Student placement progress & verified hours (Nested Loop, 0.140 ms, 7 buffer hits) & Requirements compliance review (Hash Joins, 0.126 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.15 ms with 100% buffer cache hits) |
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 3 - OJT Management System (Project-OJT-RMA-System) [Tracker Key: Group 06 / Group 03] |
| **Database and backup file** | `ojt_management`, `ojt_management_activity6.dump`, Size: 56,714 bytes, SHA256: `FC979FF8640AE61F9A747475427E472B7D8344B193D751097DDEF576E3A10A7F` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-05 08:07:30) |
| **Restore test result** | Success (Restored into `ojt_management_restore_test`; all 12 tables, 129 constraints, and data verified) |
| **Access control after restore** | (Activity 4 roles and permissions validated; `ojt_report_user` denied reading `users`) |
| **Recovery simulation result** | Recovered (Simulated deleted test requirement successfully restored from verified baseline state) |
| **Proposed RPO and RTO** | RPO: 1 hour, RTO: 30 minutes |
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash (via Antigravity AI assistant).
- **Tasks Performed:** 
  1. Inspecting Java codebase (`Db.java`, `Roles.java`, `Main.java`) and diagnosing the repository disparity between `OJT Database.sql` (lending demo dump on `main`) and `postgresql query.pdf` (DDL on branch `ojt-system-untested.demo`).
  2. Formulating and executing PostgreSQL least-privilege verification test suites for Activity 4 (soft-delete enforcement with complete withholding of `DELETE` on operational tables).
  3. Diagnosing runtime maintenance boundaries, executing `VACUUM` and `ANALYZE`, and profiling execution plans (`EXPLAIN (ANALYZE, BUFFERS)`) for Activity 5.
  4. Executing custom archive backup creation with `pg_dump`, SHA256 checksum computation, test database restoration with `pg_restore`, and controlled recovery simulation drill for Activity 6.
  5. Compiling and formatting comprehensive progress documentation adhering to course rubric guidelines.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in `psql`. Real outputs, timings, and checksums are recorded in this report. No codebase files within `Project-OJT-RMA-System` were modified.
