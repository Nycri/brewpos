# COMP 018: Database Administration â€” Activity 4: Access Control Report
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

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Initial Implementation
In the default configuration of Group 6's **HabalGo** application (`.env` and `config.php`), the backend connects using the PostgreSQL administrative account (`postgres`):
```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=grp00_habalgodb
DB_USER=postgres
DB_PASSWORD=[REDACTED]
```
Running a public-facing ride-hailing and booking web application under a superuser account directly violates the **Principle of Least Privilege (PoLP)**. An attacker exploiting an insecure API route (e.g., in `api.php`) or an injection flaw would possess unrestricted DDL and DML capabilities across the entire database cluster. They could drop tables, truncate ride transaction logs, purge customer profiles, falsify GCash/Maya payments, or read authentication secrets belonging to unrelated databases on the same host.

Furthermore, an inspection of Group 6's provided role script ([`database/03_roles_and_privileges.sql`](file:///c:/Users/oliva/OneDrive/Documents/habalgo(G06)/HabalGo/database/03_roles_and_privileges.sql)) revealed two critical architectural vulnerabilities:
1. **Cluster-Wide Role Namespace Collisions:** Group 6 defined generic role names: `app_runtime_grp` and `app_readonly_grp`. In PostgreSQL, roles are **cluster-wide objects** rather than database-scoped objects. When evaluated alongside other group databases (such as Group 7's CampusSafe which also created `app_runtime_grp`), executing Group 6's script threw role duplication errors. Worse, if existing group roles were reused without renaming, users from one project inadvertently inherited connection privileges to other databases.
2. **Blanket `DELETE` Privileges on Operational Tables:** Group 6's script executed:
   ```sql
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA habalgo TO app_runtime_grp;
   ```
   Granting blanket `DELETE` privileges across all tables (`booking`, `customer`, `driver`, `payment`, `rating_feedback`, `vehicle`, and `location`) allows an application account to delete legal financial receipts and passenger trip histories. In a transport management platform, completed trips and payments must remain immutable audit logs.
3. **Missing Dedicated Backup Role:** Group 6 omitted a backup operator role entirely. Backups would be forced to run either under `postgres` (excessive administrative privilege) or `habalgo_app_user` (violating separation of duties).

### Activity 4 Solution & Design
To address these security vulnerabilities, we developed and implemented a least-privilege Role-Based Access Control (RBAC) architecture tailored to HabalGo:
- **Project-Namespaced Group Roles (`NOLOGIN`):** Common privilege templates scoped uniquely to HabalGo to prevent cross-project collisions: `habalgo_runtime_grp`, `habalgo_readonly_grp`, and `habalgo_backup_grp`.
- **Dedicated Login Roles (`LOGIN`):** Individual authenticated accounts assigned to their corresponding group roles: `habalgo_app_user`, `habalgo_readonly_user`, and `habalgo_backup`.
- **Strictly Scoped `DELETE` Privileges (Narrow Deletion):** The runtime user `habalgo_app_user` is granted `SELECT`, `INSERT`, and `UPDATE` on operational tables, but its `DELETE` capability is strictly confined to `habalgo.driver_vehicle`. This aligns directly with `api.php` line 89 (`DELETE FROM driver_vehicle WHERE vehicle_id=?`), allowing operators to reassign or unassign motorbikes while strictly blocking deletions from `booking`, `payment`, `customer`, `driver`, `rating_feedback`, `vehicle`, and `location`.
- **Read-Only / Auditor Shielding:** `habalgo_readonly_user` is granted schema-wide `SELECT` for business auditing and trip monitoring, with an absolute prohibition on `INSERT`, `UPDATE`, `DELETE`, or DDL operations.
- **Dedicated Backup Role:** The `habalgo_backup` account is granted only the read permissions necessary to perform logical database dumps (`pg_dump`), without write or administrative capabilities.
- **Hardened PUBLIC Boundary:** Default connection and creation privileges were revoked from `PUBLIC` on both the database `grp00_habalgodb` and the `habalgo` schema.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `habalgo_app_user` | `habalgo_runtime_grp` | Application Runtime | Handles all live customer bookings, driver assignments, vehicle operations, payment logging, and ratings | • `CONNECT` on `grp00_habalgodb`<br>• `USAGE` on schema `habalgo`<br>• `SELECT, INSERT, UPDATE` on all tables in `habalgo`<br>• `DELETE` **strictly confined to `driver_vehicle`**<br>• `USAGE, SELECT` on all sequences in `habalgo` | Scoping `DELETE` strictly to `driver_vehicle` allows vehicle reassignment while mathematically preventing runtime SQL injections from deleting bookings, payments, or customer profiles. |
| `habalgo_readonly_user` | `habalgo_readonly_grp` | Auditor / Transport Compliance | Read-only inspection of ride activity, active drivers, customer feedback, and financial summaries | • `CONNECT` on `grp00_habalgodb`<br>• `USAGE` on schema `habalgo`<br>• `SELECT` on all tables in `habalgo`<br>*(Zero write/update/delete permissions)* | Allows safety regulators and financial auditors to inspect fleet operations and review metrics without risking accidental data mutation. |
| `habalgo_backup` | `habalgo_backup_grp` | Backup Operator | Executes logical database backups (`pg_dump`) | • `CONNECT` on `grp00_habalgodb`<br>• `USAGE` on schema `habalgo`<br>• `SELECT` on all tables and sequences in `habalgo` | Conforms to Activity 6 by providing complete read access required for dumps while strictly barring DML writes and DDL. |
| `postgres` | *(Superuser)* | Database Administrator | Schema migrations, index tuning, routine maintenance (`VACUUM`/`ANALYZE`), disaster recovery | Superuser / Owner privileges | Reserved strictly for DBA operations; completely eliminated from the application connection string. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script was executed as `postgres` against `grp00_habalgodb`:

```sql
-- =============================================================================
-- HabalGo: Enterprise Access Control & Least Privilege Setup
-- Target Database: grp00_habalgodb | Target Schema: habalgo
-- =============================================================================

-- 1. Detach any conflicting legacy group roles
REVOKE ALL ON DATABASE grp00_habalgodb FROM app_runtime_grp, app_readonly_grp;
REVOKE ALL ON SCHEMA habalgo FROM app_runtime_grp, app_readonly_grp;

-- 2. Create Project-Namespaced Group Roles (NOLOGIN)
CREATE ROLE habalgo_runtime_grp NOLOGIN;
CREATE ROLE habalgo_readonly_grp NOLOGIN;
CREATE ROLE habalgo_backup_grp NOLOGIN;

-- 3. Create Dedicated Login Accounts (LOGIN)
-- (Use strong passwords stored locally in secure environment configuration)
CREATE ROLE habalgo_app_user LOGIN PASSWORD 'HabalGoSecurePass2026!';
CREATE ROLE habalgo_readonly_user LOGIN PASSWORD 'HabalGoReadOnly2026!';
CREATE ROLE habalgo_backup LOGIN PASSWORD 'BackupOperatorPass2026!';

-- 4. Assign Login Accounts to Respective Group Roles
GRANT habalgo_runtime_grp TO habalgo_app_user;
GRANT habalgo_readonly_grp TO habalgo_readonly_user;
GRANT habalgo_backup_grp TO habalgo_backup;

-- 5. Revoke Insecure Default PUBLIC Privileges
REVOKE ALL ON DATABASE grp00_habalgodb FROM PUBLIC;
REVOKE ALL ON SCHEMA habalgo FROM PUBLIC;

-- 6. Grant Database Connection and Schema Usage
GRANT CONNECT ON DATABASE grp00_habalgodb TO habalgo_runtime_grp, habalgo_readonly_grp, habalgo_backup_grp;
GRANT USAGE ON SCHEMA habalgo TO habalgo_runtime_grp, habalgo_readonly_grp, habalgo_backup_grp;

-- 7. Application Runtime Permissions (Principle of Least Privilege)
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA habalgo TO habalgo_runtime_grp;
-- Strictly Confine DELETE to driver_vehicle for vehicle reassignment
GRANT DELETE ON habalgo.driver_vehicle TO habalgo_runtime_grp;
-- Grant sequence permissions for SERIAL key increments
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA habalgo TO habalgo_runtime_grp;

-- 8. Read-Only / Auditor Role Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA habalgo TO habalgo_readonly_grp;

-- 9. Dedicated Backup Operator Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA habalgo TO habalgo_backup_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA habalgo TO habalgo_backup_grp;

-- 10. Configure Default Privileges for Future Database Objects
ALTER DEFAULT PRIVILEGES IN SCHEMA habalgo GRANT SELECT, INSERT, UPDATE ON TABLES TO habalgo_runtime_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA habalgo GRANT USAGE, SELECT ON SEQUENCES TO habalgo_runtime_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA habalgo GRANT SELECT ON TABLES TO habalgo_backup_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA habalgo GRANT USAGE, SELECT ON SEQUENCES TO habalgo_backup_grp;
```

### Verification of Role Membership
```sql
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
WHERE r.rolname IN ('postgres', 'habalgo_app_user', 'habalgo_readonly_user', 'habalgo_backup', 'habalgo_runtime_grp', 'habalgo_readonly_grp', 'habalgo_backup_grp')
ORDER BY r.rolname;
```

**Actual Output:**
```text
       role_name       | can_login | is_superuser |    member_of_group     
-----------------------+-----------+--------------+------------------------
 habalgo_app_user      | t         | f            | {habalgo_runtime_grp}
 habalgo_backup        | t         | f            | {habalgo_backup_grp}
 habalgo_backup_grp    | f         | f            | {}
 habalgo_readonly_grp  | f         | f            | {}
 habalgo_readonly_user | t         | f            | {habalgo_readonly_grp}
 habalgo_runtime_grp   | f         | f            | {}
 postgres              | t         | t            | {}
(7 rows)
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `habalgo_app_user`)

### Test 1.1: Read Operational Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT booking_id, customer_id, driver_id, booking_status, booking_date 
  FROM habalgo.booking 
  ORDER BY booking_id 
  LIMIT 5;
  ```
- **Expected Result:** Allowed; returns submitted ride bookings.
- **Actual Result / Output:**
  ```text
   booking_id | customer_id | driver_id | booking_status |    booking_date     
  ------------+-------------+-----------+----------------+---------------------
            1 |           1 |         1 | Completed      | 2026-10-20 10:15:00
            2 |           2 |         2 | In Progress    | 2026-10-21 11:30:00
            3 |           1 |         3 | Confirmed      | 2026-10-22 14:00:00
            4 |           2 |           | Pending        | 2026-10-23 16:45:00
            5 |           1 |         1 | Completed      | 2026-10-24 09:00:00
  (5 rows)
  ```

### Test 1.2: Insert a Booking & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO habalgo.booking (customer_id, driver_id, pickup_location_id, dropoff_location_id, booking_date, booking_status) 
  VALUES (1, 2, 1, 3, NOW(), 'Pending') 
  RETURNING booking_id, booking_status;
  ```
- **Expected Result:** Allowed; inserts booking record and advances sequence.
- **Actual Result / Output:**
  ```text
   booking_id | booking_status 
  ------------+----------------
            6 | Pending
  (1 row)
  INSERT 0 1
  ```

### Test 1.3: Update Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE habalgo.booking 
  SET booking_status = 'Confirmed' 
  WHERE booking_id = 6 
  RETURNING booking_id, booking_status;
  ```
- **Expected Result:** Allowed; updates booking status from Pending to Confirmed.
- **Actual Result / Output:**
  ```text
   booking_id | booking_status 
  ------------+----------------
            6 | Confirmed
  (1 row)
  UPDATE 1
  ```

### Test 1.4: Delete on Authorized Table (`driver_vehicle`) (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO habalgo.driver_vehicle (driver_id, vehicle_id, assigned_from) 
  VALUES (4, 4, NOW()) RETURNING driver_id, vehicle_id;

  DELETE FROM habalgo.driver_vehicle 
  WHERE driver_id = 4 AND vehicle_id = 4;
  ```
- **Expected Result:** Allowed; runtime account can unassign vehicles.
- **Actual Result / Output:**
  ```text
   driver_id | vehicle_id 
  -----------+------------
           4 |          4
  (1 row)
  INSERT 0 1
  DELETE 1
  ```

### Test 1.5: Delete Booking Record (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM habalgo.booking WHERE booking_id = 6;
  ```
- **Expected Result:** Denied; application users must not be permitted to purge booking records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table booking
  ```

### Test 1.6: Delete Payment Record (Denied — Financial Integrity Protection)
- **SQL Statement:**
  ```sql
  DELETE FROM habalgo.payment WHERE payment_id = 1;
  ```
- **Expected Result:** Denied; payment transaction logs must remain immutable.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table payment
  ```

### Test 1.7: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE habalgo.rating_feedback;
  ```
- **Expected Result:** Denied; DDL requires database object ownership.
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table rating_feedback
  ```

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `habalgo_readonly_user`)

### Test 2.1: Read Operational Data with Multi-Table Joins (Allowed)
- **SQL Statement:**
  ```sql
  SELECT b.booking_id, c.name AS customer_name, d.name AS driver_name, 
         b.booking_status, p.amount, p.status AS payment_status
  FROM habalgo.booking b
  JOIN habalgo.customer c ON b.customer_id = c.customer_id
  LEFT JOIN habalgo.driver d ON b.driver_id = d.driver_id
  LEFT JOIN habalgo.payment p ON p.booking_id = b.booking_id
  ORDER BY b.booking_id
  LIMIT 5;
  ```
- **Expected Result:** Allowed; reporting inspection query executes cleanly.
- **Actual Result / Output:**
  ```text
   booking_id | customer_name  |  driver_name  | booking_status | amount | payment_status 
  ------------+----------------+---------------+----------------+--------+----------------
            1 | Maria Santos   | Juan Bautista | Completed      | 150.00 | Paid
            2 | Juan Dela Cruz | Carlos Gomez  | In Progress    | 220.00 | Pending
            3 | Maria Santos   | Miguel Santos | Confirmed      | 180.00 | Paid
            4 | Juan Dela Cruz |               | Pending        |        | 
            5 | Maria Santos   | Juan Bautista | Completed      | 280.00 | Paid
  (5 rows)
  ```

### Test 2.2: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO habalgo.location (address, city) VALUES ('Rogue Terminal', 'Quezon City');
  ```
- **Expected Result:** Denied; read-only account cannot insert data.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table location
  ```

### Test 2.3: Attempt to Update Records (Denied)
- **SQL Statement:**
  ```sql
  UPDATE habalgo.payment SET status = 'Refunded' WHERE payment_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot alter payment records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table payment
  ```

### Test 2.4: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM habalgo.booking WHERE booking_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot delete data.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table booking
  ```

### Test 2.5: Attempt to Truncate / DDL (Denied)
- **SQL Statement:**
  ```sql
  TRUNCATE TABLE habalgo.rating_feedback;
  ```
- **Expected Result:** Denied; read-only account cannot truncate tables.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table rating_feedback
  ```

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  CREATE DATABASE rogue_habalgo_db;
  ```
- **Expected Result:** Denied; application users must not possess administrative `CREATEDB` privileges.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied to create database
  ```

### Test 3.2: Attempt to Connect to Unrelated Databases (Denied)
- **Command A (Targeting `canteen_db`):**
  ```bash
  psql -U habalgo_app_user -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
  ```
  **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "canteen_db"
  DETAIL:  User does not have CONNECT privilege.
  ```

- **Command B (Targeting `grp07_campussafedb` after revoking generic role membership):**
  ```bash
  psql -U habalgo_app_user -h localhost -p 5432 -d grp07_campussafedb -c "SELECT 1;"
  ```
  **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "grp07_campussafedb"
  DETAIL:  User does not have CONNECT privilege.
  ```

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Cluster-Wide Role Collisions & Multi-Tenant Leakage:**
   - *Issue:* Group 6's original script defined generic role names `app_runtime_grp` and `app_readonly_grp`. Because PostgreSQL roles are cluster-wide, executing Group 6's script triggered:
     ```text
     ERROR: role "app_runtime_grp" already exists
     ERROR: role "app_readonly_grp" already exists
     ```
     Because `habalgo_app_user` was granted membership into the existing `app_runtime_grp` (configured earlier by Group 7), `habalgo_app_user` inherited `CONNECT` privileges onto `grp07_campussafedb`, allowing cross-database penetration!
   - *Fix:* Revoked `app_runtime_grp` and `app_readonly_grp` from `habalgo_app_user` and `habalgo_readonly_user`. Re-architected role templates using project-namespaced prefixes: `habalgo_runtime_grp`, `habalgo_readonly_grp`, and `habalgo_backup_grp`.
2. **Missing `ON CONFLICT` in `sample data.sql`:**
   - *Issue:* Running `sample data.sql` after `02_seed.sql` failed with:
     ```text
     ERROR: duplicate key value violates unique constraint "driver_license_no_key"
     DETAIL: Key (license_no)=(N01-23-000001) already exists.
     ```
     This occurred because `02_seed.sql` seeded drivers `Juan Bautista` and `Carlos Gomez`, and `sample data.sql` re-inserted those exact drivers without an `ON CONFLICT (license_no) DO NOTHING` clause.
   - *Fix:* Added `ON CONFLICT (license_no) DO NOTHING` to driver insert statements and synchronized serial sequences using `ALTER SEQUENCE ... RESTART WITH ...`.
3. **Database Name Discrepancy:**
   - *Issue:* Group 6's documentation and `.env` configured the database as `grp00_habalgodb` rather than `grp06_habalgodb`.
   - *Fix:* Standardized on `grp00_habalgodb` to maintain seamless compatibility with their `.env` and `api.php`.
4. **Missing Dedicated Backup Role:**
   - *Issue:* Group 6 provided no backup operator account in `database/03_roles_and_privileges.sql`.
   - *Fix:* Created `habalgo_backup_grp` and `habalgo_backup` login account to support Activity 6 compliance.

### Feedback to Owning Group (Group 6: HabalGo)
- **Commendation:** The relational schema in `01_schema.sql` is well-modeled. The choice of a custom schema (`habalgo`), check constraints on booking status (`ck_booking_status`), foreign key cascades, and unique constraints on ratings (`uq_rating_booking_customer`) provides strong relational integrity.
- **Recommendation 1 (Namespace Roles):** Always prefix group roles and login accounts with your project identifier (e.g., `habalgo_runtime_grp` instead of `app_runtime_grp`). In production shared database clusters, generic role names cause deployment failure or cross-tenant privilege escalation.
- **Recommendation 2 (Narrow `DELETE` Scope):** Replace `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES` with specific grants. Scoping `DELETE` strictly to `driver_vehicle` prevents accidental or malicious loss of bookings, payment logs, and customer data.
- **Recommendation 3 (Seed Script Idempotency):** In `sample data.sql`, append `ON CONFLICT (license_no) DO NOTHING` to all insert statements so the script can be run multiple times without crashing on unique key violations.

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 6 - HabalGo: Ride and Booking Management System |
| **Repository cloned** | `https://github.com/jeanaroyo/HabalGo`, Branch: `main` |
| **Database setup result** | Success (Initialized database grp00_habalgodb, created schema habalgo, loaded 8 tables, sequences, and operational seed data). |
| **Access-control folder link** | |
| **Access-control status** | Completed |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

---
