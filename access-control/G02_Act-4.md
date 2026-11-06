# COMP 018: Database Administration â€” Activity 4: Access Control Report
## Group Evaluation Report: Group 02 â€” Park-It: Parking Management System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Park-It: Parking Management System** (Park-It Database Management System) |
| **Target Project Group** | **Group 2:** De Vera Miller P., Butanas John David, Macascas Mark Andrew, Pelia Jhon Francis, Lumbo Marianne |
| **Target Project Repository** | [https://github.com/millerdevera6-glitch/park-it-database](https://github.com/millerdevera6-glitch/park-it-database) |
| **Primary Database** | `grp_parkitdb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp_parkitdb_restore_test` |

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Initial Activity 3 Implementation
In the initial Activity 3 setup of Group 2's Park-It system, the Node.js/Express application connected using the PostgreSQL superuser (`postgres`) configured directly inside `.env` (`DB_USER=postgres`). Running an interactive web application under a superuser account violates the **Principle of Least Privilege (PoLP)**. If an unauthenticated user or an attacker were to exploit a SQL injection or unauthorized REST API endpoint, they would possess unrestricted DDL and DML capabilities — allowing them to drop critical tables, purge vehicle check-in histories, manipulate billing transactions, or tamper with cluster configurations.

### Activity 4 Solution & Design
To secure Group 2's **Park-It** database, we implemented a robust Role-Based Access Control (RBAC) hierarchy that strictly separates application runtime execution, compliance reporting, and administrative database maintenance:
- **Group Roles (`NOLOGIN`):** Architectural privilege templates that define specific capability boundaries (`parkit_app_grp`, `parkit_readonly_grp`, and `parkit_backup_grp`).
- **Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their respective group roles (`parkit_app`, `parkit_report_user`, and `parkit_backup`).
- **Narrow Deletion (`DELETE` Scoping):** The runtime account `parkit_app` is granted `SELECT`, `INSERT`, and `UPDATE` on operational tables, but its `DELETE` capability is strictly confined to `parking_zone` (allowing administrators to decommission empty parking lots as implemented in `server.js` route `DELETE /api/zones/:id`). Crucially, `parkit_app` is **denied `DELETE` on `transaction`, `payment`, `vehicle`, and `staff_user`**. This guarantees that vehicle audit trails and payment financial records cannot be deleted through runtime API calls.
- **Auditor Credential Protection:** The read-only reporting account `parkit_report_user` is granted `SELECT` on `parking_zone`, `vehicle`, `transaction`, and `payment` for operational auditing, but is **explicitly denied `SELECT` on `staff_user`**. This shields sensitive employee bcrypt password hashes from auditors and reporting tools.
- **Dedicated Backup Role:** Logical backups are decoupled from application accounts and delegated to `parkit_backup`, a read-only account possessing schema-wide `SELECT` without write, update, or schema alteration permissions.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `parkit_app` | `parkit_app_grp` | Application Runtime | Handles all live parking zone operations, vehicle check-ins, occupancy counters, and checkout billing | • `CONNECT` on `grp_parkitdb`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on all 5 tables (`parking_zone`, `vehicle`, `staff_user`, `transaction`, `payment`)<br>• `DELETE` **strictly on `parking_zone`**<br>• `USAGE, SELECT` on all sequences | Confining `DELETE` strictly to `parking_zone` ensures that transactional and billing histories (`transaction`, `payment`) cannot be purged under any circumstances, preserving financial and legal auditability. |
| `parkit_report_user` | `parkit_readonly_grp` | Parking Auditor / Finance Inspector | Read-only inspection of parking occupancy, vehicle transaction logs, and daily payment receipts | • `CONNECT` on `grp_parkitdb`<br>• `USAGE` on schema `public`<br>• `SELECT` on `parking_zone`, `vehicle`, `transaction`, `payment`<br>*(No access to `staff_user`)* | Enables complete compliance auditing without the ability to tamper with records or rates. Excluding `staff_user` protects staff authentication credentials from exposure. |
| `parkit_backup` | `parkit_backup_grp` | Backup Operator | Executes logical database dumps (`pg_dump`) | • `CONNECT` on `grp_parkitdb`<br>• `USAGE` on schema `public`<br>• `SELECT` on all tables & sequences | Grants the comprehensive read access required for logical dumps while strictly preventing DDL or DML write operations. |
| `postgres` | *(Superuser)* | Database Administrator | Schema provisioning, migrations, maintenance (`VACUUM`/`ANALYZE`), disaster recovery | Superuser / Owner privileges | Reserved strictly for DBA operations; completely removed from the application runtime connection pool. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script was executed as `postgres` against `grp_parkitdb`:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE parkit_app_grp NOLOGIN;
CREATE ROLE parkit_readonly_grp NOLOGIN;
CREATE ROLE parkit_backup_grp NOLOGIN;

-- 2. Create Login Roles (LOGIN)
CREATE ROLE parkit_app LOGIN PASSWORD 'AppRuntimePass2026!';
CREATE ROLE parkit_report_user LOGIN PASSWORD 'ReportUserPass2026!';
CREATE ROLE parkit_backup LOGIN PASSWORD 'BackupOperatorPass2026!';

-- 3. Assign Role Memberships
GRANT parkit_app_grp TO parkit_app;
GRANT parkit_readonly_grp TO parkit_report_user;
GRANT parkit_backup_grp TO parkit_backup;

-- 4. Lock down PUBLIC permissions
REVOKE ALL ON DATABASE grp_parkitdb FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 5. Database Connection and Schema Usage
GRANT CONNECT ON DATABASE grp_parkitdb TO parkit_app_grp, parkit_readonly_grp, parkit_backup_grp;
GRANT USAGE ON SCHEMA public TO parkit_app_grp, parkit_readonly_grp, parkit_backup_grp;

-- 6. Application Runtime Permissions (Principle of Least Privilege)
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO parkit_app_grp;
GRANT DELETE ON parking_zone TO parkit_app_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO parkit_app_grp;

-- 7. Read-Only / Auditor Role Permissions (Excluding sensitive staff_user)
GRANT SELECT ON parking_zone, vehicle, transaction, payment TO parkit_readonly_grp;

-- 8. Backup Operator Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO parkit_backup_grp;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO parkit_backup_grp;

-- 9. Default Privileges for Future Tables & Sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO parkit_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO parkit_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO parkit_backup_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO parkit_backup_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `parkit_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT zone_id, zone_name, max_capacity, current_active_count FROM parking_zone LIMIT 5;
  ```
- **Expected Result:** Allowed; returns active parking zones.
- **Actual Result / Output:**
  ```text
   zone_id |       zone_name        | max_capacity | current_active_count 
  ---------+------------------------+--------------+----------------------
         1 | Zone A                 |           50 |                    0
         2 | Zone B - Covered       |           40 |                    5
         3 | Zone C - VIP / Faculty |           20 |                    1
         4 | Zone D - Motorcycle    |           80 |                   12
         5 | Zone E - Overflow      |          100 |                    0
  (5 rows)
  ```
- **Status:** PASS

### Test 1.2: Insert a Zone & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO parking_zone (zone_name, max_capacity, current_active_count) 
  VALUES ('Activity 4 Test Zone', 25, 0) 
  RETURNING zone_id, zone_name;
  ```
- **Expected Result:** Allowed; inserts record and advances `parking_zone_zone_id_seq`.
- **Actual Result / Output:**
  ```text
   zone_id |      zone_name       
  ---------+----------------------
         6 | Activity 4 Test Zone
  (1 row)
  INSERT 0 1
  ```
- **Status:** PASS

### Test 1.3: Update Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE parking_zone SET current_active_count = 1 WHERE zone_id = 6 RETURNING zone_id, current_active_count;
  ```
- **Expected Result:** Allowed; runtime account updates occupancy counter.
- **Actual Result / Output:**
  ```text
   zone_id | current_active_count 
  ---------+----------------------
         6 |                    1
  (1 row)
  UPDATE 1
  ```
- **Status:** PASS

### Test 1.4: Delete Zone (Allowed — Narrow `DELETE` on `parking_zone`)
- **SQL Statement:**
  ```sql
  DELETE FROM parking_zone WHERE zone_id = 6 RETURNING zone_id, zone_name;
  ```
- **Expected Result:** Allowed; runtime account is authorized to remove decommissioned zones as per API specification.
- **Actual Result / Output:**
  ```text
   zone_id |      zone_name       
  ---------+----------------------
         6 | Activity 4 Test Zone
  (1 row)
  DELETE 1
  ```
- **Status:** PASS

### Test 1.5: Delete Transaction Record (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM transaction WHERE transaction_id = 1;
  ```
- **Expected Result:** Denied; runtime account must never delete vehicle audit history.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table transaction
  ```
- **Status:** PASS

### Test 1.6: Delete Staff User Record (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM staff_user WHERE user_id = 1;
  ```
- **Expected Result:** Denied; runtime account cannot delete employee identities.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table staff_user
  ```
- **Status:** PASS

### Test 1.7: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE parking_zone;
  ```
- **Expected Result:** Denied; DDL requires object ownership (`postgres`).
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table parking_zone
  ```
- **Status:** PASS

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `parkit_report_user`)

### Test 2.1: Read Parking Zones & Joined Transactions / Vehicles (Allowed)
- **SQL Statement:**
  ```sql
  SELECT t.transaction_id, v.license_plate, z.zone_name, t.entry_time, t.status 
  FROM transaction t 
  JOIN vehicle v ON t.vehicle_id = v.vehicle_id 
  JOIN parking_zone z ON t.zone_id = z.zone_id 
  LIMIT 5;
  ```
- **Expected Result:** Allowed; auditor can inspect multi-table parking logs.
- **Actual Result / Output:**
  ```text
   transaction_id | license_plate |       zone_name        |     entry_time      |  status   
  ----------------+---------------+------------------------+---------------------+-----------
                1 | ABC-1234      | Zone A                 | 2026-11-04 08:30:00 | Completed
                2 | XYZ-8888      | Zone B - Covered       | 2026-11-04 09:00:00 | Active
                3 | NCR-4567      | Zone D - Motorcycle    | 2026-11-04 09:15:00 | Completed
                4 | PUV-9901      | Zone A                 | 2026-11-04 10:00:00 | Active
                5 | TXI-2026      | Zone C - VIP / Faculty | 2026-11-04 07:45:00 | Completed
  (5 rows)
  ```
- **Status:** PASS

### Test 2.2: Attempt to Read User Credentials (Denied — Credential Protection)
- **SQL Statement:**
  ```sql
  SELECT * FROM staff_user;
  ```
- **Expected Result:** Denied; auditor role has no `SELECT` privilege on `staff_user` to shield bcrypt password hashes.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table staff_user
  ```
- **Status:** PASS

### Test 2.3: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO parking_zone (zone_name, max_capacity, current_active_count) VALUES ('Unauthorized Zone', 10, 0);
  ```
- **Expected Result:** Denied; read-only account cannot insert data.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table parking_zone
  ```
- **Status:** PASS

### Test 2.4: Attempt to Update Records (Denied)
- **SQL Statement:**
  ```sql
  UPDATE parking_zone SET max_capacity = 999 WHERE zone_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot alter parking limits.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table parking_zone
  ```
- **Status:** PASS

### Test 2.5: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM parking_zone WHERE zone_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot delete data.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table parking_zone
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
  psql -U parkit_app -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
  ```
- **Expected Result:** Denied; cross-database access is blocked by revoked PUBLIC privileges.
- **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "canteen_db"
  DETAIL:  User does not have CONNECT privilege.
  ```
- **Status:** PASS

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Initial Superuser DSN Configuration:** The application's `.env.example` and local `.env` defaulted to `DB_USER=postgres`. We decoupled this vulnerability by defining `parkit_app_grp` and `parkit_app`, scoping runtime permissions strictly to necessary operations.
2. **Missing Sample Data in `seed.sql`:** The repository's `seed.sql` provided only a single row for `parking_zone` (`Zone A`), leaving `vehicle`, `staff_user`, `transaction`, and `payment` unpopulated. We expanded sample records in the local test database to enable comprehensive testing of multi-table joins, payment billing, and foreign keys.
3. **Environment Variable Naming Discrepancies:** In `server.js`, database parameters checked both `process.env.DB_USERNAME || process.env.DB_USER` and `process.env.DB_DATABASE || process.env.DB_NAME`. Recommending standardized keys prevents runtime initialization errors.

### Feedback to Owning Group (Group 2: Park-It)
- **Commendation:** The relational schema is well-designed with strict constraints: `parking_zone_check` (`current_active_count <= max_capacity`), `payment_amount_check` (`amount >= 0`), and clean foreign key cascades.
- **Recommendation 1:** Switch `.env.example` to reference `parkit_app` instead of `postgres` to ensure peer evaluators deploy the application with least privilege by default.
- **Recommendation 2:** Expand `seed.sql` in the repository to include sample vehicles, transactions, and staff accounts so all routes in `testing.guide` can be fully exercised out-of-the-box.

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 2 - Park-It: Parking Management System |
| **Repository cloned** | `https://github.com/millerdevera6-glitch/park-it-database`, Branch: `main` |
| **Database setup result** | Success (Verified PostgreSQL 18.6 on port 5432, schema built with 5 tables, populated sample data, configured least-privilege roles). |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/db-access-control/group-02-parkit/` |
| **Access-control status** | Completed |
| **Feedback sent to owners** | Yes (05/11/2026) |
| **PDF progress document** | *(Link to your group's shared PDF document)* |
| **Last updated** | 2026-11-05 |

---
