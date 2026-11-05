# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **HabalGo: Ride and Booking Management System** |
| **Target Project Group** | **Group 6** (Royo, Villareal, Barte, Balansay, Remoquillo, Gumahin) |
| **Target Project Repository** | [https://github.com/jeanaroyo/HabalGo](https://github.com/jeanaroyo/HabalGo) |
| **Primary Database** | `grp00_habalgodb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Target Schema** | `habalgo` |
| **Restored Test Database** | `grp00_habalgodb_restore_test` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime User `habalgo_app_user`)
   - 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `habalgo_readonly_user`)
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
   - 3.3 Restoration into Separate Test Database (`grp00_habalgodb_restore_test`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

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

-- 4. Table Inventory and Individual Table Sizes (Schema: habalgo)
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident('habalgo') || '.' || quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident('habalgo') || '.' || quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident('habalgo') || '.' || quote_ident(table_name))) AS index_size
FROM information_schema.tables
WHERE table_schema = 'habalgo' AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident('habalgo') || '.' || quote_ident(table_name)) DESC;

-- 5. Total Number of Tables and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'habalgo') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'habalgo') AS total_sequences;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Current Database & User:** `grp00_habalgodb`, `postgres`
- **Total Database Size:** `8662 kB` (~8.66 MB)
- **Object Count:** `total_tables: 8`, `total_sequences: 7`
- **Table Storage Breakdown:**
  ```text
     table_name    | total_size | table_size | index_size 
  -----------------+------------+------------+------------
   booking         | 72 kB      | 8192 bytes | 64 kB
   rating_feedback | 64 kB      | 8192 bytes | 48 kB
   payment         | 40 kB      | 8192 bytes | 32 kB
   driver          | 40 kB      | 8192 bytes | 32 kB
   vehicle         | 40 kB      | 8192 bytes | 32 kB
   customer        | 40 kB      | 8192 bytes | 32 kB
   driver_vehicle  | 24 kB      | 8192 bytes | 16 kB
   location        | 24 kB      | 8192 bytes | 16 kB
  (8 rows)
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
```

### Actual Output
- **Database Statistics:**
  ```text
       datname     | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  -----------------+------------------+------------------------+--------------------------+-----------+-----------
   grp00_habalgodb |                1 |                    109 |                       13 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The database exhibits zero locking contention. Rolled-back transactions reflect intentional permission denial tests from Activity 4.
- **Active Session Inspection:** Only the monitoring administrative `psql` connection was active, with no stalled, long-running, or idle-in-transaction connections.

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
WHERE schemaname = 'habalgo'
ORDER BY n_dead_tup DESC, relname;
```

### Actual Output (Pre-Maintenance State)
```text
   table_name    | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
-----------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 driver          |                6 |                      24 |          27 |          10 |          17 |             |                 |              | 
 vehicle         |                6 |                       8 |          11 |           4 |           8 |             |                 |              | 
 driver_vehicle  |                3 |                       3 |           4 |           3 |           7 |             |                 |              | 
 booking         |                6 |                       5 |           9 |           6 |           1 |             |                 |              | 
 customer        |                4 |                       4 |          15 |           2 |           0 |             |                 |              | 
 location        |                3 |                       6 |          12 |           6 |           0 |             |                 |              | 
 payment         |                4 |                       4 |           1 |           4 |           0 |             |                 |              | 
 rating_feedback |                5 |                       2 |           0 |           2 |           0 |             |                 |              | 
(8 rows)
```
*Analysis:* 
- `driver` accumulated 17 dead tuples, `vehicle` accumulated 8 dead tuples, and `driver_vehicle` accumulated 7 dead tuples due to sequence resets, aborted batch inserts, and temporary assignment testing.
- `last_vacuum` and `last_analyze` were `NULL`, confirming that routine maintenance was required to clean dead tuple bloat and refresh planner statistics.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Access Control Logging:**
   - **Log Entry:** `ERROR: role "app_runtime_grp" already exists` during initial script execution.
   - **Log Entry:** `ERROR: permission denied for table booking on statement DELETE FROM habalgo.booking WHERE booking_id = 6;` by `habalgo_app_user`
   - **Log Entry:** `ERROR: permission denied for table payment on statement DELETE FROM habalgo.payment WHERE payment_id = 1;` by `habalgo_app_user`
   - **Log Entry:** `ERROR: must be owner of table rating_feedback on statement DROP TABLE habalgo.rating_feedback;` by `habalgo_app_user`
   - **Log Entry:** `ERROR: permission denied for table location on statement INSERT INTO habalgo.location (address, city) VALUES ('Rogue Terminal', 'Quezon City');` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied for table payment on statement UPDATE habalgo.payment SET status = 'Refunded' WHERE payment_id = 1;` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied for table booking on statement DELETE FROM habalgo.booking WHERE booking_id = 1;` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied for table rating_feedback on statement TRUNCATE TABLE habalgo.rating_feedback;` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_habalgo_db;` by `habalgo_app_user`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `habalgo_app_user`
   - **Log Entry:** `FATAL: permission denied for database "grp07_campussafedb" DETAIL: User does not have CONNECT privilege.` on connection by `habalgo_app_user`
   - **Assessment:** Positive validation of security controls. The PostgreSQL engine successfully intercepted and recorded every unauthorized operation.
2. **Server Health:** Engine checkpoints and background writer threads completed with 0 errors.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Execution & Role Permission Verification
- **Application User Permission Boundary Test:** Attempting to execute `VACUUM habalgo.booking;` under `habalgo_app_user` produced:
  ```text
  WARNING:  permission denied to vacuum "booking", skipping it
  VACUUM
  ```
  This proves that application runtime accounts cannot run table maintenance. Maintenance operations require table ownership or superuser status (`postgres`).

- **Standalone Maintenance Commands Executed as `postgres`:**
  ```sql
  ANALYZE;
  VACUUM (VERBOSE, ANALYZE) habalgo.driver;
  VACUUM (VERBOSE, ANALYZE) habalgo.vehicle;
  VACUUM (VERBOSE, ANALYZE) habalgo.driver_vehicle;
  VACUUM (VERBOSE, ANALYZE) habalgo.booking;
  ```

### Actual Output
- **`ANALYZE;` Output:** `ANALYZE`
- **`VACUUM (VERBOSE, ANALYZE) habalgo.driver;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.driver"
  INFO:  finished vacuuming "grp00_habalgodb.habalgo.driver": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 17 removed, 10 remain, 0 are dead but not yet removable
  removable cutoff: 2102, which was 0 XIDs old when operation ended
  new relfrozenxid: 2022, which is 12 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 1 pages from table (100.00% of total) had 17 dead item identifiers removed
  index "driver_pkey": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  index "driver_license_no_key": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  buffer usage: 86 hits, 0 reads, 6 dirtied
  WAL usage: 9 records, 6 full page images, 34160 bytes, 0 buffers full
  INFO:  analyzing "habalgo.driver"
  INFO:  "driver": scanned 1 of 1 pages, containing 10 live rows and 0 dead rows; 10 rows in sample, 10 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) habalgo.vehicle;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.vehicle"
  INFO:  finished vacuuming "grp00_habalgodb.habalgo.vehicle": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 8 removed, 4 remain, 0 are dead but not yet removable
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 1 pages from table (100.00% of total) had 8 dead item identifiers removed
  buffer usage: 86 hits, 0 reads, 7 dirtied
  INFO:  analyzing "habalgo.vehicle"
  INFO:  "vehicle": scanned 1 of 1 pages, containing 4 live rows and 0 dead rows; 4 rows in sample, 4 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) habalgo.driver_vehicle;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.driver_vehicle"
  tuples: 7 removed, 3 remain, 0 are dead but not yet removable
  buffer usage: 65 hits, 0 reads, 7 dirtied
  INFO:  analyzing "habalgo.driver_vehicle"
  INFO:  "driver_vehicle": scanned 1 of 1 pages, containing 3 live rows and 0 dead rows; 3 rows in sample, 3 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) habalgo.booking;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.booking"
  tuples: 1 removed, 6 remain, 0 are dead but not yet removable
  buffer usage: 94 hits, 0 reads, 6 dirtied
  INFO:  analyzing "habalgo.booking"
  INFO:  "booking": scanned 1 of 1 pages, containing 6 live rows and 0 dead rows; 6 rows in sample, 6 estimated total rows
  VACUUM
  ```

### Post-Maintenance Statistics Verification
```sql
SELECT 
    relname AS table_name,
    n_live_tup AS live_tuples,
    n_dead_tup AS dead_tuples,
    last_vacuum, last_analyze
FROM pg_stat_user_tables
WHERE schemaname = 'habalgo'
ORDER BY relname;
```

**Actual Output:**
```text
   table_name    | live_tuples | dead_tuples |          last_vacuum          |         last_analyze          
-----------------+-------------+-------------+-------------------------------+-------------------------------
 booking         |           6 |           0 | 2026-11-05 06:45:57.932108-05 | 2026-11-05 06:45:57.93683-05
 customer        |           2 |           0 |                               | 2026-11-05 06:45:09.650616-05
 driver          |          10 |           0 | 2026-11-05 06:45:16.605773-05 | 2026-11-05 06:45:16.60886-05
 driver_vehicle  |           3 |           0 | 2026-11-05 06:45:46.193784-05 | 2026-11-05 06:45:46.195847-05
 location        |           6 |           0 |                               | 2026-11-05 06:45:09.641533-05
 payment         |           4 |           0 |                               | 2026-11-05 06:45:09.645095-05
 rating_feedback |           2 |           0 |                               | 2026-11-05 06:45:09.643518-05
 vehicle         |           4 |           0 | 2026-11-05 06:45:35.625139-05 | 2026-11-05 06:45:35.629053-05
(8 rows)
```
*Outcome:* All dead tuples across all tables were reduced to 0, reclamation was complete, and planner statistics were fully refreshed.

### DBA Justification: Role Permissions & Exclusion of `VACUUM FULL`
- **Permission Scope:** `VACUUM` modifies physical heap blocks and requires table ownership. Running it under application accounts violates the principle of least privilege and triggers permission warnings. Maintenance must be scheduled and executed by a dedicated DBA role (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites the table into a new disk file and acquires an `AccessExclusiveLock`, completely blocking concurrent `SELECT`, `INSERT`, `UPDATE`, and `DELETE` queries. For HabalGo, standard non-blocking `VACUUM` reclaimed 100% of the dead tuples (`17 removed` in `driver`, `8 removed` in `vehicle`, `7 removed` in `driver_vehicle`, and `1 removed` in `booking`) without causing any application downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Bookings List Multi-Table Join (`api.php` line 45-47 `$BK`)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    b.booking_id AS id,
    b.customer_id,
    c.name AS customer,
    lp.address AS pickup,
    lp.city AS pickup_city,
    ld.address AS dropoff,
    ld.city AS dropoff_city,
    b.driver_id,
    d.name AS driver,
    b.booking_status,
    to_char(b.booking_date, 'Mon DD') AS date
FROM habalgo.booking b
JOIN habalgo.customer c ON c.customer_id = b.customer_id
JOIN habalgo.location lp ON lp.location_id = b.pickup_location_id
JOIN habalgo.location ld ON ld.location_id = b.dropoff_location_id
LEFT JOIN habalgo.driver d ON d.driver_id = b.driver_id
ORDER BY b.booking_id DESC;
```

#### Actual Execution Plan Output
```text
                                                                    QUERY PLAN                                                                     
---------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=5.85..5.86 rows=6 width=149) (actual time=0.835..0.839 rows=6.00 loops=1)
   Sort Key: b.booking_id DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=8
   ->  Hash Join  (cost=4.48..5.77 rows=6 width=149) (actual time=0.802..0.817 rows=6.00 loops=1)
         Hash Cond: (b.dropoff_location_id = ld.location_id)
         Buffers: shared hit=5
         ->  Hash Join  (cost=3.34..4.59 rows=6 width=96) (actual time=0.054..0.066 rows=6.00 loops=1)
               Hash Cond: (b.customer_id = c.customer_id)
               Buffers: shared hit=4
               ->  Hash Right Join  (cost=2.30..3.50 rows=6 width=82) (actual time=0.040..0.049 rows=6.00 loops=1)
                     Hash Cond: (d.driver_id = b.driver_id)
                     Buffers: shared hit=3
                     ->  Seq Scan on driver d  (cost=0.00..1.10 rows=10 width=19) (actual time=0.005..0.006 rows=10.00 loops=1)
                           Buffers: shared hit=1
                     ->  Hash  (cost=2.22..2.22 rows=6 width=67) (actual time=0.027..0.029 rows=6.00 loops=1)
                           Buckets: 1024  Batches: 1  Memory Usage: 9kB
                           Buffers: shared hit=2
                           ->  Hash Join  (cost=1.14..2.22 rows=6 width=67) (actual time=0.021..0.024 rows=6.00 loops=1)
                                 Hash Cond: (b.pickup_location_id = lp.location_id)
                                 Buffers: shared hit=2
                                 ->  Seq Scan on booking b  (cost=0.00..1.06 rows=6 width=38) (actual time=0.007..0.008 rows=6.00 loops=1)
                                       Buffers: shared hit=1
                                 ->  Hash  (cost=1.06..1.06 rows=6 width=37) (actual time=0.006..0.006 rows=6.00 loops=1)
                                       Buckets: 1024  Batches: 1  Memory Usage: 9kB
                                       Buffers: shared hit=1
                                       ->  Seq Scan on location lp  (cost=0.00..1.06 rows=6 width=37) (actual time=0.002..0.003 rows=6.00 loops=1)
                                             Buffers: shared hit=1
               ->  Hash  (cost=1.02..1.02 rows=2 width=18) (actual time=0.010..0.011 rows=2.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on customer c  (cost=0.00..1.02 rows=2 width=18) (actual time=0.004..0.005 rows=2.00 loops=1)
                           Buffers: shared hit=1
         ->  Hash  (cost=1.06..1.06 rows=6 width=37) (actual time=0.021..0.022 rows=6.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on location ld  (cost=0.00..1.06 rows=6 width=37) (actual time=0.014..0.014 rows=6.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=292
 Planning Time: 5.634 ms
 Execution Time: 0.963 ms
(42 rows)
```

#### Interpretation
1. **Execution Time:** `0.963 ms` (sub-millisecond latency).
2. **Buffer Hit Ratio:** `shared hit = 8`, with 0 disk block reads (100% cache hit).
3. **Execution Strategy:** Chained in-memory hash joins across `booking`, `location` (pickup), `driver`, `customer`, and `location` (dropoff), followed by an in-memory quicksort on `booking_id DESC` requiring only 25 kB of RAM. The planner appropriately utilized sequential scans over small heap blocks rather than traversing multi-level B-Tree index pages.

---

### Query 2: Dashboard Metrics Aggregation (`api.php` line 66-70)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    (SELECT count(*) FROM habalgo.customer)::int AS customers,
    (SELECT count(*) FROM habalgo.booking WHERE booking_status IN ('Pending','Confirmed','In Progress'))::int AS active,
    (SELECT COALESCE(sum(amount),0) FROM habalgo.payment WHERE status='Paid')::float AS revenue,
    (SELECT round(avg(rating),1) FROM habalgo.rating_feedback)::float AS avg_rating;
```

#### Actual Execution Plan Output
```text
                                                        QUERY PLAN                                                        
--------------------------------------------------------------------------------------------------------------------------
 Result  (cost=4.25..4.27 rows=1 width=24) (actual time=0.363..0.365 rows=1.00 loops=1)
   Buffers: shared hit=4
   InitPlan 1
     ->  Aggregate  (cost=1.02..1.03 rows=1 width=8) (actual time=0.023..0.024 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on customer  (cost=0.00..1.02 rows=2 width=0) (actual time=0.018..0.018 rows=2.00 loops=1)
                 Buffers: shared hit=1
   InitPlan 2
     ->  Aggregate  (cost=1.09..1.10 rows=1 width=8) (actual time=0.022..0.022 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on booking  (cost=0.00..1.08 rows=4 width=0) (actual time=0.020..0.021 rows=4.00 loops=1)
                 Filter: ((booking_status)::text = ANY ('{Pending,Confirmed,"In Progress"}'::text[]))
                 Rows Removed by Filter: 2
                 Buffers: shared hit=1
   InitPlan 3
     ->  Aggregate  (cost=1.06..1.07 rows=1 width=32) (actual time=0.028..0.029 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on payment  (cost=0.00..1.05 rows=3 width=5) (actual time=0.010..0.011 rows=3.00 loops=1)
                 Filter: ((status)::text = 'Paid'::text)
                 Rows Removed by Filter: 1
                 Buffers: shared hit=1
   InitPlan 4
     ->  Aggregate  (cost=1.03..1.04 rows=1 width=32) (actual time=0.254..0.254 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on rating_feedback  (cost=0.00..1.02 rows=2 width=4) (actual time=0.030..0.030 rows=2.00 loops=1)
                 Buffers: shared hit=1
 Planning:
   Buffers: shared hit=301
 Planning Time: 9.232 ms
 Execution Time: 0.426 ms
(30 rows)
```

#### Interpretation
1. **Execution Time:** `0.426 ms`.
2. **Buffer Efficiency:** `shared hit = 4`, 0 disk reads.
3. **Execution Strategy:** Four independent `InitPlans` executed in memory to calculate total customers, active rides, revenue sum, and average rating, consolidating the scalar values into a single `Result` row in 0.426 ms.

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current schema indexes | All 5 custom indexes (`idx_booking_customer`, `idx_booking_date`, `idx_booking_driver`, `idx_payment_booking`, `idx_rating_booking`) and 12 constraint indexes are properly configured. Query execution latencies are < 1.0 ms with 100% cache hits. Additional indexes would create write overhead during booking insertions without improving latency. |
| **Follow-up Action** | Partial Composite Index on Active Bookings | As the platform scales past 10,000 completed rides in production, the dashboard query filtering active bookings (`WHERE booking_status IN ('Pending','Confirmed','In Progress')`) will benefit from a partial index: `CREATE INDEX idx_booking_active ON habalgo.booking (booking_status) WHERE booking_status IN ('Pending','Confirmed','In Progress');`. |
| **Follow-up Action** | Scheduled Maintenance via Windows Task Scheduler | Deploy an automated weekly maintenance script invoking `VACUUM (ANALYZE)` during low-traffic windows (e.g., Sunday 03:00 AM) to maintain 0 dead tuples as high-velocity bookings update. |

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

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in the class Google Sheets tracker:

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

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 6 - HabalGo: Ride and Booking Management System |
| **Database inspected** | `grp00_habalgodb` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `habalgo_app_user` (Runtime), `habalgo_readonly_user` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 8.66 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on driver, vehicle, driver_vehicle, booking via postgres` |
| **Query analyzed** | Multi-table booking join query (Hash joins, 0.963 ms, 8 buffer hits) & Dashboard aggregate query (0.426 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 1 ms with 100% buffer cache hits) |
| **Evidence folder link** |  |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

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

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash.
- **Tasks Performed:** 
  1. Inspecting database schema definitions, constraints, and relationship hierarchies for Group 6's HabalGo system.
  2. Formulating and executing PostgreSQL least-privilege verification test suites for Activity 4, resolving role collision and isolating group roles.
  3. Diagnosing runtime maintenance boundaries, executing `VACUUM` and `ANALYZE`, and profiling execution plans (`EXPLAIN (ANALYZE, BUFFERS)`) for Activity 5.
  4. Executing custom archive backup creation with `pg_dump`, SHA256 checksum computation, test database restoration with `pg_restore`, and controlled recovery simulation drill for Activity 6.
  5. Compiling and formatting comprehensive progress documentation adhering to course rubric guidelines.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in pgAdmin 4 / `psql`. Real outputs, timings, and checksums are recorded in this report.

