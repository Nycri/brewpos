# COMP 018: Database Administration - Activity 4: Access Control Report
## Group Evaluation Report: Group 03 - OJT Management System

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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 3 - OJT Management System (Project-OJT-RMA-System) [Tracker Key: Group 06 / Group 03] |
| **Repository cloned** | `https://github.com/swvenz/Project-OJT-RMA-System`, Branches: `main`, `ojt-system-untested.demo` |
| **Database setup result** | Success with issues (Resolved repository artifact mismatch where `OJT Database.sql` on `main` was an unrelated demo lending dump; extracted official schema from `ojt-system-untested.demo`, repaired supervisor column discrepancies, and deployed full 12-table relational schema with RBAC). |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/access-control` |
| **Access-control status** | Completed |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---
