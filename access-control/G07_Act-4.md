# COMP 018: Database Administration - Activity 4: Access Control Report
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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 7 - CampusSafe: Campus Hazard & Safety Reporting System |
| **Repository cloned** | `https://github.com/jeffersonorpesa2-ops/grp07-campussafe`, Branch: `main` |
| **Database setup result** | Success (Configured user php.ini with pdo_pgsql, verified PostgreSQL 18 on port 5432, loaded 9 tables and seed data). |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/access-control` |
| **Access-control status** | Completed |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

---
