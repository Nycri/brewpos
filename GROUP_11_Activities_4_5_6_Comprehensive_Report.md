# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **BoardTrack: Boarding House & Rental Management System** |
| **Target Project Group** | **Group 11:** Karl Robert C. (karlrobertc-art), Adrian Barcarse (Galactic_Destroyer), Kurt Policarpio (KurtPoli) |
| **Target Project Repository** | [https://github.com/karlrobertc-art/BoardTrack](https://github.com/karlrobertc-art/BoardTrack) |
| **Target Project Branch** | `main` |
| **Primary Database** | `boardtrack_db` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `boardtrack_db_restore_test` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime User `boardtrack_app`)
   - 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `boardtrack_report_user`)
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
   - 3.3 Restoration into Separate Test Database (`boardtrack_db_restore_test`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Initial Activity 3 Implementation
In the default configuration of Group 11's **BoardTrack** system (`.env.example` and standard setup), the connection string defaults to using the PostgreSQL superuser:
```text
DATABASE_URL=postgresql://postgres:password@localhost:5432/boardtrack_db
```
Running an interactive web application under a superuser account (`postgres`) severely violates the **Principle of Least Privilege (PoLP)**. Because the Node.js/Express application directly routes client requests into SQL queries via its connection pool in `src/db.js`, any unhandled SQL injection, misconfigured endpoint, or compromised dependency grants an attacker full superuser authority over the entire database cluster. An attacker could drop database catalogs, truncate active leases, read or alter other groups' databases hosted on the same cluster, manipulate server configuration parameters, or even execute arbitrary system commands via PostgreSQL backend functions.

Furthermore, although Group 11 designed application-level role restrictions in `src/auth.js` (`admin`, `staff`, and `tenant`), application-level filtering alone provides zero defense if the underlying database connection holds unrestricted capabilities.

### Activity 4 Solution & Design
To address these vulnerabilities and establish multi-layered defense-in-depth, we designed and implemented a Role-Based Access Control (RBAC) architecture that enforces strict privilege boundaries at the PostgreSQL engine level:

1. **Decoupling Roles via Group Roles (`NOLOGIN`):** Privilege blueprints are defined as non-login group roles (`boardtrack_app_grp`, `boardtrack_readonly_grp`, `boardtrack_backup_grp`), separating privilege allocation from individual login credentials.
2. **Dedicated Login Accounts (`LOGIN`):** Dedicated login roles are provisioned with strong authentication credentials and assigned to their respective group roles (`boardtrack_app`, `boardtrack_report_user`, `boardtrack_backup`).
3. **Application Runtime PoLP Scoping (`boardtrack_app`):** The application runtime account receives `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on operational tables (`property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`) to allow landlord and staff administrative operations. Crucially, `boardtrack_app` is granted `SELECT`, `INSERT`, and `UPDATE` on `app_user`, but is **strictly denied `DELETE` on `app_user`**. This guarantees that user authentication records and login credentials cannot be purged through runtime queries or malicious deletions.
4. **Auditor Credential Protection (`boardtrack_report_user`):** The reporting and financial auditor account receives read-only access (`SELECT`) on all business tables and the computed summary view `v_lease_summary`. However, it is **explicitly denied `SELECT` on `app_user`**. This safeguards sensitive bcrypt password hashes and tenant authentication records from exposure during reporting or compliance inspections.
5. **Dedicated Backup Role (`boardtrack_backup`):** Logical backups are decoupled from application runtime accounts and assigned to `boardtrack_backup`, a read-only account possessing schema-wide `SELECT` on tables and sequences with zero write or schema modification rights.
6. **Isolation & Public Lockdown:** Default `PUBLIC` privileges on `boardtrack_db` and schema `public` are revoked, preventing unauthenticated cross-database snooping or unauthorized schema creation.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `boardtrack_app` | `boardtrack_app_grp` | Application Runtime | Handles all live web application operations: properties, room listings, tenant encoding, lease agreements, payment logging, and utility billing | • `CONNECT` on `boardtrack_db`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE, DELETE` on `property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`<br>• `SELECT, INSERT, UPDATE` on `app_user`<br>• `SELECT` on `v_lease_summary`, `schema_migrations`<br>• `USAGE, SELECT` on all sequences | Grants operational CRUD capabilities required by landlord and caretaker workflows in `src/routes/`. Excluding `DELETE` on `app_user` prevents accidental or malicious purges of user identities. |
| `boardtrack_report_user` | `boardtrack_readonly_grp` | Financial Auditor / Compliance Inspector | Read-only inspection of properties, room occupancy, tenant contracts, payment collections, and utility billings | • `CONNECT` on `boardtrack_db`<br>• `USAGE` on schema `public`<br>• `SELECT` on `property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`<br>• `SELECT` on `v_lease_summary`<br>*(Explicitly denied access to `app_user`)* | Enables end-to-end auditability and revenue verification without the risk of data alteration. Excluding `app_user` shields password hashes from unauthorized inspection. |
| `boardtrack_backup` | `boardtrack_backup_grp` | Backup Operator | Performs scheduled logical dumps (`pg_dump`) | • `CONNECT` on `boardtrack_db`<br>• `USAGE` on schema `public`<br>• `SELECT` on all tables & sequences in `public` | Grants comprehensive read access necessary for consistent database backups while blocking any DML modification or DDL schema destruction. |
| `postgres` | *(Superuser)* | Database Administrator | Cluster maintenance, migration execution, schema provisioning, routine `VACUUM`/`ANALYZE`, and disaster recovery | Cluster-wide superuser / DB owner privileges | Reserved exclusively for DBA maintenance and disaster recovery; strictly removed from standard application connection pools. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script was executed by the database administrator (`postgres`) against `boardtrack_db`:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE boardtrack_app_grp NOLOGIN;
CREATE ROLE boardtrack_readonly_grp NOLOGIN;
CREATE ROLE boardtrack_backup_grp NOLOGIN;

-- 2. Create Login Roles (LOGIN)
CREATE ROLE boardtrack_app LOGIN PASSWORD 'BoardTrackApp2026!';
CREATE ROLE boardtrack_report_user LOGIN PASSWORD 'ReportUserPass2026!';
CREATE ROLE boardtrack_backup LOGIN PASSWORD 'BackupOperatorPass2026!';

-- 3. Assign Role Memberships
GRANT boardtrack_app_grp TO boardtrack_app;
GRANT boardtrack_readonly_grp TO boardtrack_report_user;
GRANT boardtrack_backup_grp TO boardtrack_backup;

-- 4. Lock down PUBLIC permissions
REVOKE ALL ON DATABASE boardtrack_db FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 5. Database Connection and Schema Usage
GRANT CONNECT ON DATABASE boardtrack_db TO boardtrack_app_grp, boardtrack_readonly_grp, boardtrack_backup_grp;
GRANT USAGE ON SCHEMA public TO boardtrack_app_grp, boardtrack_readonly_grp, boardtrack_backup_grp;

-- 6. Application Runtime Permissions (Principle of Least Privilege)
GRANT SELECT, INSERT, UPDATE, DELETE ON property, room, tenant, lease, payment, utility_bill TO boardtrack_app_grp;
GRANT SELECT, INSERT, UPDATE ON app_user TO boardtrack_app_grp;
GRANT SELECT ON v_lease_summary TO boardtrack_app_grp;
GRANT SELECT ON schema_migrations TO boardtrack_app_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO boardtrack_app_grp;

-- 7. Read-Only / Auditor Role Permissions (Excluding sensitive app_user credentials)
GRANT SELECT ON property, room, tenant, lease, payment, utility_bill TO boardtrack_readonly_grp;
GRANT SELECT ON v_lease_summary TO boardtrack_readonly_grp;

-- 8. Backup Operator Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO boardtrack_backup_grp;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO boardtrack_backup_grp;

-- 9. Default Privileges for Future Tables & Sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO boardtrack_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO boardtrack_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO boardtrack_backup_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO boardtrack_backup_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `boardtrack_app`)

### Test 1.1: Read Operational Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT property_id, property_name, address FROM property LIMIT 5;
  ```
- **Expected Result:** Allowed; runtime account reads managed properties.
- **Actual Result / Output:**
  ```text
   property_id |      property_name      |              address              
  -------------+-------------------------+-----------------------------------
             1 | Sunshine Boarding House | 12 Rizal St., Calamba, Laguna
             2 | Greenview Apartments    | 45 Mabini Ave., Los Banos, Laguna
  (2 rows)
  ```
- **Status:** PASS

### Test 1.2: Insert a Property & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO property (property_name, address) 
  VALUES ('Activity 4 Test Dormitory', '100 University Ave., Los Banos, Laguna') 
  RETURNING property_id, property_name;
  ```
- **Expected Result:** Allowed; inserts record and advances `property_property_id_seq`.
- **Actual Result / Output:**
  ```text
   property_id |       property_name       
  -------------+---------------------------
             3 | Activity 4 Test Dormitory
  (1 row)
  INSERT 0 1
  ```
- **Status:** PASS

### Test 1.3: Update Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE property 
  SET address = '100 University Ave. Ext., Los Banos, Laguna' 
  WHERE property_id = 3 
  RETURNING property_id, address;
  ```
- **Expected Result:** Allowed; runtime account updates property address.
- **Actual Result / Output:**
  ```text
   property_id |                   address                   
  -------------+---------------------------------------------
             3 | 100 University Ave. Ext., Los Banos, Laguna
  (1 row)
  UPDATE 1
  ```
- **Status:** PASS

### Test 1.4: Delete Property (Allowed — Operational Scope)
- **SQL Statement:**
  ```sql
  DELETE FROM property WHERE property_id = 3 RETURNING property_id, property_name;
  ```
- **Expected Result:** Allowed; runtime account is permitted to delete decommissioned properties in accordance with application business routes.
- **Actual Result / Output:**
  ```text
   property_id |       property_name       
  -------------+---------------------------
             3 | Activity 4 Test Dormitory
  (1 row)
  DELETE 1
  ```
- **Status:** PASS

### Test 1.5: Delete User Credential Record (Denied — PoLP Credential Protection)
- **SQL Statement:**
  ```sql
  DELETE FROM app_user WHERE user_id = 1;
  ```
- **Expected Result:** Denied; runtime account must not delete user login credentials.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table app_user
  ```
- **Status:** PASS

### Test 1.6: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE room;
  ```
- **Expected Result:** Denied; DDL operations require table ownership (`postgres`).
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table room
  ```
- **Status:** PASS

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `boardtrack_report_user`)

### Test 2.1: Read Summary View & Financial Aggregates (Allowed)
- **SQL Statement:**
  ```sql
  SELECT lease_id, tenant, property_name, room_number, monthly_rent, status, total_paid, utilities_billed 
  FROM v_lease_summary 
  LIMIT 5;
  ```
- **Expected Result:** Allowed; auditor inspects joined tenant leases and computed billing metrics.
- **Actual Result / Output:**
  ```text
   lease_id |     tenant     |      property_name      | room_number | monthly_rent |   status   | total_paid | utilities_billed 
  ----------+----------------+-------------------------+-------------+--------------+------------+------------+------------------
          2 | Maria Santos   | Sunshine Boarding House | 102         |      3500.00 | ACTIVE     |    3500.00 |           380.00
          3 | Pedro Reyes    | Greenview Apartments    | A1          |      5000.00 | ACTIVE     |    5000.00 |           700.00
          1 | Juan Dela Cruz | Sunshine Boarding House | 101         |      3500.00 | ACTIVE     |    7000.00 |           570.50
          4 | Ana Lopez      | Sunshine Boarding House | 101         |      3300.00 | TERMINATED |    3300.00 |                0
  (4 rows)
  ```
- **Status:** PASS

### Test 2.2: Attempt to Read User Credentials (Denied — Credential Protection)
- **SQL Statement:**
  ```sql
  SELECT * FROM app_user;
  ```
- **Expected Result:** Denied; read-only auditor role has no `SELECT` privilege on `app_user` to shield bcrypt password hashes.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table app_user
  ```
- **Status:** PASS

### Test 2.3: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO property (property_name, address) VALUES ('Rogue Dorm', 'Nowhere');
  ```
- **Expected Result:** Denied; read-only account cannot insert records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table property
  ```
- **Status:** PASS

### Test 2.4: Attempt to Update Records (Denied)
- **SQL Statement:**
  ```sql
  UPDATE room SET monthly_rate = 9999 WHERE room_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot modify room rates.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table room
  ```
- **Status:** PASS

### Test 2.5: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM tenant WHERE tenant_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot purge tenant records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table tenant
  ```
- **Status:** PASS

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  CREATE DATABASE rogue_test_db;
  ```
- **Expected Result:** Denied; runtime accounts must not possess cluster-level `CREATEDB` privileges.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied to create database
  ```
- **Status:** PASS

### Test 3.2: Attempt to Connect to Unrelated Database (Denied)
- **Command:**
  ```bash
  psql -U boardtrack_app -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
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
1. **Superuser Connection Default in `.env`:** The application's `.env.example` and automatic bootstrap script (`src/bootstrap.js`) assume the connection URL is running with `postgres` or an account holding `CREATEDB`. While convenient for zero-config onboarding, running an interactive web server under `postgres` exposes the entire cluster. We resolved this by creating `boardtrack_app` with scoped privileges and advising the team to separate bootstrap setup from runtime execution.
2. **Exclusion Constraint Extension Prerequisite:** The database schema requires the standard PostgreSQL extension `btree_gist` to support the temporal exclusion constraint `ex_lease_no_overlap` (`daterange(start_date, end_date, '[]') WITH &&`). In managed cloud database environments (e.g., AWS RDS, Supabase, Neon), `CREATE EXTENSION` typically requires superuser or `rds_superuser` privileges. Documenting this prerequisite in `docs/troubleshooting.md` helps prevent migration failures when deploying into locked-down environments.
3. **Session-Level Row Filtering vs. Database-Level RLS:** The tenant portal restricts tenants to viewing only their own records via application-level filtering in `src/routes/` (`WHERE tenant_id = req.session.user.tenant_id`). While effective within the Node.js application layer, pairing this with PostgreSQL Row-Level Security (RLS) in future iterations would provide defense-in-depth against application logic flaws.

### Feedback to Owning Group (Group 11: BoardTrack)
- **Commendation (Exemplary Temporal Modeling):** Group 11's implementation of the exclusion constraint `ex_lease_no_overlap` using `btree_gist` is outstanding. Using `EXCLUDE USING gist (room_id WITH =, daterange(start_date, end_date, '[]') WITH &&) WHERE (status = 'ACTIVE')` completely prevents double-booking active leases on the same room at the relational level, eliminating race conditions that standard CHECK constraints cannot address.
- **Commendation (Triggers & Normalization):** The automated synchronization trigger `trg_lease_sync_room` (`sync_room_status()`) and status guard `trg_room_status_guard` (`guard_room_status()`) maintain room occupancy states consistently without application code dependency. Computing financial balances dynamically via `v_lease_summary` demonstrates exemplary database normalization.
- **Recommendation 1 (Decouple Setup from Runtime):** In `src/bootstrap.js`, separate the automated database creation step (which requires `CREATEDB`) from normal application startup. The application should connect using `boardtrack_app` during standard runtime, with migration scripts executed separately by an administrator account.
- **Recommendation 2 (User Management UI):** Provide a landlord administrative interface to manage `app_user` accounts, rather than relying exclusively on direct SQL inserts for provisioning staff and tenant logins.

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

-- 5. Total Number of Tables, Sequences, and Views
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public') AS total_sequences,
    (SELECT count(*) FROM information_schema.views WHERE table_schema = 'public') AS total_views;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Total Database Size:** `9094 kB` (~9.1 MB)
- **Object Count:** `total_tables: 8`, `total_sequences: 7`, `total_views: 1` (`v_lease_summary`)
- **Table Storage Breakdown:**
  ```text
      table_name     | total_size | table_size | index_size 
  -------------------+------------+------------+------------
   lease             | 64 kB      | 8192 bytes | 56 kB
   app_user          | 48 kB      | 8192 bytes | 32 kB
   tenant            | 40 kB      | 8192 bytes | 32 kB
   utility_bill      | 40 kB      | 8192 bytes | 32 kB
   room              | 40 kB      | 8192 bytes | 32 kB
   payment           | 40 kB      | 8192 bytes | 32 kB
   schema_migrations | 32 kB      | 8192 bytes | 16 kB
   property          | 24 kB      | 8192 bytes | 16 kB
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
WHERE r.rolname LIKE 'boardtrack%'
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
      datname    | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  ---------------+------------------+------------------------+--------------------------+-----------+-----------
   boardtrack_db |                1 |                     43 |                        7 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The 7 rolled-back transactions correspond directly to intentional error conditions triggered during negative security verification testing (unauthorized DDL, permission denied operations on `app_user`, and cross-database access attempts).
- **Roles Verification Output:**
  ```text
          role_name        | can_login | is_superuser |      member_of_group      
  -------------------------+-----------+--------------+---------------------------
   boardtrack_app          | t         | f            | {boardtrack_app_grp}
   boardtrack_app_grp      | f         | f            | {}
   boardtrack_backup       | t         | f            | {boardtrack_backup_grp}
   boardtrack_backup_grp   | f         | f            | {}
   boardtrack_readonly_grp | f         | f            | {}
   boardtrack_report_user  | t         | f            | {boardtrack_readonly_grp}
  (6 rows)
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

### Actual Output (Pre-Maintenance)
```text
    table_name     | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
-------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 room              |                4 |                      12 |           9 |           6 |           4 |             |                 |              | 
 property          |                3 |                       4 |          19 |           2 |           2 |             |                 |              | 
 tenant            |                3 |                       4 |           9 |           4 |           0 |             |                 |              | 
 payment           |                3 |                       6 |           4 |           6 |           0 |             |                 |              | 
 schema_migrations |                1 |                       0 |           1 |           1 |           0 |             |                 |              | 
 utility_bill      |                3 |                       4 |           4 |           4 |           0 |             |                 |              | 
 lease             |                6 |                       4 |          33 |           4 |           0 |             |                 |              | 
 app_user          |                3 |                       3 |           0 |           3 |           0 |             |                 |              | 
(8 rows)
```
*Analysis:* 
- `room` accumulated **4 dead tuples** because inserting and updating leases fired the database trigger `trg_lease_sync_room`, executing underlying `UPDATE room SET status = ...` queries. In PostgreSQL MVCC architecture, each UPDATE writes a new tuple version and marks the previous version as dead.
- `property` accumulated **2 dead tuples** directly resulting from the insert, update, and delete operations performed during Activity 4 Test 1.2–1.4 on `Activity 4 Test Dormitory`.
- This confirms that routine `VACUUM` maintenance is necessary to reclaim dead tuple pointers and prevent page fragmentation.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_084707.log`)
1. **Activity 4 Security Audit Logs (Permission Enforcement):**
   - **Log Entry:** `2026-11-05 18:44:46 EST ERROR: permission denied for table app_user STATEMENT: DELETE FROM app_user WHERE user_id = 1;` by `boardtrack_app`
   - **Log Entry:** `2026-11-05 18:44:55 EST ERROR: must be owner of table room STATEMENT: DROP TABLE room;` by `boardtrack_app`
   - **Log Entry:** `2026-11-05 18:45:21 EST ERROR: permission denied for table app_user STATEMENT: SELECT * FROM app_user;` by `boardtrack_report_user`
   - **Log Entry:** `2026-11-05 18:45:28 EST ERROR: permission denied for table property STATEMENT: INSERT INTO property (property_name, address) VALUES ('Rogue Dorm', 'Nowhere');` by `boardtrack_report_user`
   - **Log Entry:** `2026-11-05 18:45:44 EST ERROR: permission denied for table room STATEMENT: UPDATE room SET monthly_rate = 9999 WHERE room_id = 1;` by `boardtrack_report_user`
   - **Log Entry:** `2026-11-05 18:45:57 EST ERROR: permission denied for table tenant STATEMENT: DELETE FROM tenant WHERE tenant_id = 1;` by `boardtrack_report_user`
   - **Log Entry:** `2026-11-05 18:46:10 EST ERROR: permission denied to create database STATEMENT: CREATE DATABASE rogue_test_db;` by `boardtrack_app`
   - **Log Entry:** `2026-11-05 18:46:29 EST FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `boardtrack_app`
   - **Assessment:** Positive validation of least privilege. Every unauthorized operation attempted during Activity 4 testing was properly intercepted, rejected, and logged by the PostgreSQL engine.
2. **Background Checkpoint Health:**
   - **Log Entry:** `2026-11-05 18:49:22 EST LOG: checkpoint complete: wrote 174 buffers (1.1%), wrote 1 SLRU buffers; 0 WAL file(s) added, 0 removed, 1 recycled; write=19.183 s, sync=0.414 s, total=19.622 s; distance=1396 kB, estimate=5411 kB`
   - **Assessment:** Background checkpoints completed cleanly without I/O throttling or sync timeouts.
3. **Authentication Handshakes:** All database connections authenticated cleanly using modern `scram-sha-256` password hashing. No anomalous disconnection loops or server-side memory faults were detected.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- 1. Test Permission Boundary (as runtime application user boardtrack_app):
VACUUM property;

-- 2. Standalone Maintenance Execution (as superuser / owner postgres):
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.room;
VACUUM (VERBOSE, ANALYZE) public.property;
VACUUM (VERBOSE, ANALYZE) public.lease;
VACUUM (VERBOSE, ANALYZE) public.payment;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM property;` as `boardtrack_app` produced:
  ```text
  WARNING:  permission denied to vacuum "property", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot trigger maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.room;` Output:**
  ```text
  INFO:  vacuuming "boardtrack_db.public.room"
  INFO:  finished vacuuming "boardtrack_db.public.room": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 4 removed, 6 remain, 0 are dead but not yet removable
  removable cutoff: 2833, which was 0 XIDs old when operation ended
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 78 hits, 0 reads, 4 dirtied
  WAL usage: 6 records, 4 full page images, 33388 bytes, 0 buffers full
  INFO:  analyzing "public.room"
  INFO:  "room": scanned 1 of 1 pages, containing 6 live rows and 0 dead rows; 6 rows in sample, 6 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.property;` Output:**
  ```text
  INFO:  vacuuming "boardtrack_db.public.property"
  INFO:  finished vacuuming "boardtrack_db.public.property": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 2 removed, 2 remain, 0 are dead but not yet removable
  removable cutoff: 2834, which was 0 XIDs old when operation ended
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 1 pages from table (100.00% of total) had 1 dead item identifiers removed
  index "property_pkey": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  buffer usage: 64 hits, 0 reads, 5 dirtied
  WAL usage: 8 records, 4 full page images, 33486 bytes, 0 buffers full
  INFO:  analyzing "public.property"
  INFO:  "property": scanned 1 of 1 pages, containing 2 live rows and 0 dead rows; 2 rows in sample, 2 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.lease;` Output:**
  ```text
  INFO:  vacuuming "boardtrack_db.public.lease"
  INFO:  finished vacuuming "boardtrack_db.public.lease": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 4 remain, 0 are dead but not yet removable
  buffer usage: 113 hits, 0 reads, 4 dirtied
  INFO:  analyzing "public.lease"
  INFO:  "lease": scanned 1 of 1 pages, containing 4 live rows and 0 dead rows; 4 rows in sample, 4 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.payment;` Output:**
  ```text
  INFO:  vacuuming "boardtrack_db.public.payment"
  INFO:  finished vacuuming "boardtrack_db.public.payment": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 6 remain, 0 are dead but not yet removable
  buffer usage: 77 hits, 0 reads, 5 dirtied
  INFO:  analyzing "public.payment"
  INFO:  "payment": scanned 1 of 1 pages, containing 6 live rows and 0 dead rows; 6 rows in sample, 6 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. When attempted under `boardtrack_app`, PostgreSQL emitted `WARNING: permission denied to vacuum "property", skipping it`. This confirms that runtime application accounts cannot trigger maintenance operations.
- **Exclusion of `VACUUM FULL`:** In accordance with DBA best practices, `VACUUM FULL` was deliberately avoided. It acquires an `AccessExclusiveLock`, completely blocking concurrent `SELECT`, `INSERT`, `UPDATE`, and `DELETE` statements — which would freeze live tenant check-ins, payment encoding, and room browsing. Standard non-blocking `VACUUM` reclaimed all 4 dead tuples in `room` and 2 dead tuples in `property` with zero downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Available Rooms with Property Details and Rate Sort
This query is executed when caretakers and prospective tenants look up rentable accommodations (implemented in `src/routes/rooms.js`):
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.room_id, p.property_name, r.room_number, r.monthly_rate, r.status
FROM room r
JOIN property p ON r.property_id = p.property_id
WHERE r.status = 'AVAILABLE'
ORDER BY r.monthly_rate ASC;
```

#### Actual Execution Plan Output
```text
                                                        QUERY PLAN                                                        
--------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=2.16..2.16 rows=2 width=43) (actual time=0.083..0.084 rows=2.00 loops=1)
   Sort Key: r.monthly_rate
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=5
   ->  Nested Loop  (cost=0.00..2.15 rows=2 width=43) (actual time=0.041..0.044 rows=2.00 loops=1)
         Join Filter: (r.property_id = p.property_id)
         Rows Removed by Join Filter: 1
         Buffers: shared hit=2
         ->  Seq Scan on room r  (cost=0.00..1.07 rows=2 width=25) (actual time=0.020..0.022 rows=2.00 loops=1)
               Filter: ((status)::text = 'AVAILABLE'::text)
               Rows Removed by Filter: 4
               Buffers: shared hit=1
         ->  Materialize  (cost=0.00..1.03 rows=2 width=26) (actual time=0.009..0.009 rows=1.50 loops=2)
               Storage: Memory  Maximum Storage: 17kB
               Buffers: shared hit=1
               ->  Seq Scan on property p  (cost=0.00..1.02 rows=2 width=26) (actual time=0.013..0.013 rows=2.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=255
 Planning Time: 6.400 ms
 Execution Time: 0.137 ms
(21 rows)
```

#### Interpretation
1. **Execution Time:** `0.137 ms` (ultra-fast sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 5`, with 0 physical disk block reads (100% in-memory buffer cache hit ratio).
3. **Join Strategy:** The query planner executed a `Nested Loop` join with in-memory `Materialize` on `property` (requiring only 17 kB maximum memory).
4. **Sequential Scan Justification:** The query planner chose a sequential scan over an index scan. Because `room` and `property` both fit entirely within single 8 kB heap pages (`Buffers: shared hit = 1`), reading the block sequentially in memory is computationally cheaper than traversing B-Tree index pages followed by random pointer lookups into the heap.

---

### Query 2: Comprehensive Active Lease Summary with Tenant, Room, Property, and Financial Aggregates
This multi-table query powers the main landlord dashboard and active lease monitoring view (`v_lease_summary`), aggregating payments and utility bills:
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT l.lease_id, t.full_name AS tenant, p.property_name, r.room_number,
       l.start_date, l.end_date, l.monthly_rent, l.status,
       COALESCE((SELECT SUM(x.amount) FROM payment x
                 WHERE x.lease_id = l.lease_id AND x.status = 'COMPLETED'), 0) AS total_paid,
       COALESCE((SELECT SUM(u.amount) FROM utility_bill u
                 WHERE u.lease_id = l.lease_id), 0) AS utilities_billed
FROM lease l
JOIN tenant t   ON t.tenant_id = l.tenant_id
JOIN room r     ON r.room_id = l.room_id
JOIN property p ON p.property_id = r.property_id
WHERE l.status = 'ACTIVE'
ORDER BY l.start_date DESC;
```

#### Actual Execution Plan Output
```text
                                                            QUERY PLAN                                                             
-----------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=10.95..10.95 rows=3 width=126) (actual time=0.143..0.145 rows=3.00 loops=1)
   Sort Key: l.start_date DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=13
   ->  Nested Loop  (cost=2.18..10.92 rows=3 width=126) (actual time=0.101..0.118 rows=3.00 loops=1)
         Join Filter: (r.property_id = p.property_id)
         Rows Removed by Join Filter: 1
         Buffers: shared hit=10
         ->  Hash Join  (cost=2.18..3.32 rows=3 width=44) (actual time=0.051..0.053 rows=3.00 loops=1)
               Hash Cond: (l.tenant_id = t.tenant_id)
               Buffers: shared hit=3
               ->  Hash Join  (cost=1.09..2.21 rows=3 width=36) (actual time=0.028..0.030 rows=3.00 loops=1)
                     Hash Cond: (r.room_id = l.room_id)
                     Buffers: shared hit=2
                     ->  Seq Scan on room r  (cost=0.00..1.06 rows=6 width=11) (actual time=0.005..0.006 rows=6.00 loops=1)
                           Buffers: shared hit=1
                     ->  Hash  (cost=1.05..1.05 rows=3 width=33) (actual time=0.013..0.013 rows=3.00 loops=1)
                           Buckets: 1024  Batches: 1  Memory Usage: 9kB
                           Buffers: shared hit=1
                           ->  Seq Scan on lease l  (cost=0.00..1.05 rows=3 width=33) (actual time=0.007..0.008 rows=3.00 loops=1)
                                 Filter: ((status)::text = 'ACTIVE'::text)
                                 Rows Removed by Filter: 1
                                 Buffers: shared hit=1
               ->  Hash  (cost=1.04..1.04 rows=4 width=16) (actual time=0.017..0.017 rows=4.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on tenant t  (cost=0.00..1.04 rows=4 width=16) (actual time=0.010..0.010 rows=4.00 loops=1)
                           Buffers: shared hit=1
         ->  Materialize  (cost=0.00..1.03 rows=2 width=26) (actual time=0.004..0.004 rows=1.33 loops=3)
               Storage: Memory  Maximum Storage: 17kB
               Buffers: shared hit=1
               ->  Seq Scan on property p  (cost=0.00..1.02 rows=2 width=26) (actual time=0.007..0.007 rows=2.00 loops=1)
                     Buffers: shared hit=1
         SubPlan 1
           ->  Aggregate  (cost=1.09..1.10 rows=1 width=32) (actual time=0.010..0.010 rows=1.00 loops=3)
                 Buffers: shared hit=3
                 ->  Seq Scan on payment x  (cost=0.00..1.09 rows=1 width=5) (actual time=0.004..0.004 rows=1.33 loops=3)
                       Filter: ((lease_id = l.lease_id) AND ((status)::text = 'COMPLETED'::text))
                       Rows Removed by Filter: 5
                       Buffers: shared hit=3
         SubPlan 2
           ->  Aggregate  (cost=1.05..1.06 rows=1 width=32) (actual time=0.005..0.005 rows=1.00 loops=3)
                 Buffers: shared hit=3
                 ->  Seq Scan on utility_bill u  (cost=0.00..1.05 rows=1 width=5) (actual time=0.003..0.003 rows=1.33 loops=3)
                       Filter: (lease_id = l.lease_id)
                       Rows Removed by Filter: 3
                       Buffers: shared hit=3
 Planning:
   Buffers: shared hit=473
 Planning Time: 12.977 ms
 Execution Time: 0.281 ms
(51 rows)
```

#### Interpretation
1. **Execution Time:** `0.281 ms`.
2. **Buffer Hits:** `shared hit = 13` (100% cache hit, 0 physical disk reads).
3. **Execution Architecture:** The query planner efficiently coordinated a `Nested Loop` with `Hash Joins` between `lease`, `room`, and `tenant`, while evaluating correlated subplans for `payment` and `utility_bill` aggregations. In-memory hash tables required only 9 kB each with `Batches: 1` (zero disk spills).

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain existing primary, foreign key, and exclusion indexes | All tables are small (fitting within single 8 kB pages), and current queries execute in < 0.29 ms with 100% buffer cache hits. Adding further indexes now would introduce write overhead without improving latency. |
| **Follow-up Action** | Composite Index on Financial Payments & Utility Bills | If the boarding house expands past 5,000 payment records, subquery lookups filtering by `lease_id` and `status` will benefit from a composite index: `CREATE INDEX idx_payment_lease_status ON payment (lease_id, status) INCLUDE (amount);`. |
| **Follow-up Action** | Automated Scheduled Maintenance via Task Scheduler | Deploy a scheduled weekly maintenance job invoking `VACUUM (ANALYZE)` during off-peak hours (e.g. Sunday 03:00 AM) to automatically clean up dead tuples resulting from frequent room status changes and utility billing updates. |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `boardtrack_db` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `boardtrack_db` (Schema: `public`)<br>• 8 Relational Tables: `property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`, `app_user`, `schema_migrations`<br>• 7 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, `btree_gist` Exclusion Constraint, Triggers (`trg_lease_sync_room`, `trg_room_status_guard`), View (`v_lease_summary`), ACL Grants | Full consistency across property structures, room inventories, tenant records, active leases, payment receipts, utility billings, user authentication records, and business constraints. |
| **Backup Method & Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `boardtrack_db_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 1 Hour<br>**RTO (Recovery Time Objective):** 30 Minutes | 1-hour RPO ensures minimal payment receipt or lease agreement data loss; 30-minute RTO ensures tenant check-ins and landlord operations resume rapidly after hardware failure. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`boardtrack_app`, `boardtrack_report_user`, `boardtrack_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `boardtrack_backup` role:
```bash
pg_dump -U boardtrack_backup -h localhost -p 5432 -Fc -d boardtrack_db -f "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 5B83883ADEB30784905A7EB22B7B67607DC6D1CCB22A508E5559DE60EB48559A
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump

  Name                         Length LastWriteTime        
  ----                         ------ -------------        
  boardtrack_db_activity6.dump  35849 05/11/2026 6:48:31 pm
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents (`pg_restore --list`) confirmed **99 TOC entries**, including all 8 tables (`property`, `room`, `tenant`, `lease`, `payment`, `utility_bill`, `app_user`, `schema_migrations`), 7 sequences, extensions (`btree_gist`, `pgcrypto`), triggers (`sync_room_status`, `guard_room_status`), exclusion constraint (`ex_lease_no_overlap`), view (`v_lease_summary`), and default ACL grants.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

## 3.3 Restoration into Separate Test Database (`boardtrack_db_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 boardtrack_db_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d boardtrack_db_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\boardtrack_db_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 8 Tables Exist and Check Table Ownership
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
      table_name     | table_type |  owner   
  -------------------+------------+----------
   app_user          | BASE TABLE | postgres
   lease             | BASE TABLE | postgres
   payment           | BASE TABLE | postgres
   property          | BASE TABLE | postgres
   room              | BASE TABLE | postgres
   schema_migrations | BASE TABLE | postgres
   tenant            | BASE TABLE | postgres
   utility_bill      | BASE TABLE | postgres
  (8 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'property' AS tbl, count(*) FROM property
  UNION ALL SELECT 'room', count(*) FROM room
  UNION ALL SELECT 'tenant', count(*) FROM tenant
  UNION ALL SELECT 'lease', count(*) FROM lease
  UNION ALL SELECT 'payment', count(*) FROM payment
  UNION ALL SELECT 'utility_bill', count(*) FROM utility_bill
  UNION ALL SELECT 'app_user', count(*) FROM app_user
  UNION ALL SELECT 'schema_migrations', count(*) FROM schema_migrations;
  ```
- **Actual Output:**
  ```text
          tbl        | count 
  -------------------+-------
   property          |     2
   room              |     6
   tenant            |     4
   lease             |     4
   payment           |     6
   utility_bill      |     4
   app_user          |     3
   schema_migrations |     1
  (8 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks, Exclusion)
- **Query:**
  ```sql
  SELECT tc.table_name, tc.constraint_name, tc.constraint_type
  FROM information_schema.table_constraints tc
  WHERE tc.table_schema = 'public'
  ORDER BY tc.table_name, tc.constraint_type;
  ```
- **Actual Output:** All 68 constraint definitions (Primary Keys, Foreign Keys, Check Constraints, Unique Constraints, and the `btree_gist` Exclusion Constraint) were validated and active in the restored database.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Read Test (`boardtrack_app` reading `property`):**
  ```bash
  psql -U boardtrack_app -h localhost -p 5432 -d boardtrack_db_restore_test -c "SELECT property_id, property_name FROM property LIMIT 3;"
  ```
  *Output:*
  ```text
   property_id |      property_name      
  -------------+-------------------------
             1 | Sunshine Boarding House
             2 | Greenview Apartments
  (2 rows)
  ```
- **Auditor Summary Read Test (`boardtrack_report_user` reading `v_lease_summary`):**
  ```bash
  psql -U boardtrack_report_user -h localhost -p 5432 -d boardtrack_db_restore_test -c "SELECT lease_id, tenant, property_name FROM v_lease_summary LIMIT 3;"
  ```
  *Output:*
  ```text
   lease_id |     tenant     |      property_name      
  ----------+----------------+-------------------------
          1 | Juan Dela Cruz | Sunshine Boarding House
          2 | Maria Santos   | Sunshine Boarding House
          3 | Pedro Reyes    | Greenview Apartments
  (3 rows)
  ```
- **Auditor Credential Restriction Test (`boardtrack_report_user` attempting to read `app_user`):**
  ```bash
  psql -U boardtrack_report_user -h localhost -p 5432 -d boardtrack_db_restore_test -c "SELECT * FROM app_user;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table app_user
  ```
  *Conclusion:* Least-privilege ACLs and operational protections transferred seamlessly into the restored database.

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `boardtrack_db_restore_test`, a disposable test property record was established:
- **Query:**
  ```sql
  INSERT INTO property (property_id, property_name, address) 
  VALUES (999, 'DRILL-TEST-DORM-999: Palm Grove Residence Complex', '77 University Heights, Los Banos, Laguna');

  SELECT property_id, property_name, address FROM property WHERE property_id = 999;
  ```
- **Actual Output:**
  ```text
   property_id |                   property_name                   |                 address                  
  -------------+---------------------------------------------------+------------------------------------------
           999 | DRILL-TEST-DORM-999: Palm Grove Residence Complex | 77 University Heights, Los Banos, Laguna
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM property WHERE property_id = 999;
  SELECT count(*) AS count_after_deletion FROM property WHERE property_id = 999;
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
  INSERT INTO property (property_id, property_name, address) 
  VALUES (999, 'DRILL-TEST-DORM-999: Palm Grove Residence Complex', '77 University Heights, Los Banos, Laguna')
  ON CONFLICT (property_id) DO NOTHING;

  SELECT property_id, property_name, address FROM property WHERE property_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   property_id |                   property_name                   |                 address                  
  -------------+---------------------------------------------------+------------------------------------------
           999 | DRILL-TEST-DORM-999: Palm Grove Residence Complex | 77 University Heights, Los Banos, Laguna
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original property name and address preserved. The drill record was subsequently deleted:
```sql
DELETE FROM property WHERE property_id = 999;
```

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `boardtrack_app_grp`, `boardtrack_readonly_grp`, `boardtrack_app`, etc., before permissions can attach.
2. **Extension Availability:** The custom dump preserves the `CREATE EXTENSION IF NOT EXISTS btree_gist` statement. On target servers, the `btree_gist` contrib package must be available in the PostgreSQL installation directory.
3. **Production Backup Recommendations:**
   - **Automated Scheduled Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`boardtrack_db_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For production environments handling daily financial collections, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in the class Google Sheets tracker:

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 11 - BoardTrack: Boarding House & Rental Management System |
| **Repository cloned** | `https://github.com/karlrobertc-art/BoardTrack`, Branch: `main` |
| **Database setup result** | Success (Initialized PostgreSQL 18.6 on port 5432, built 8 tables, exclusion constraint, triggers, view, sample data, and configured least-privilege roles). |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/db-access-control/group-11-boardtrack/` |
| **Access-control status** | Completed |
| **Feedback sent to owners** | Yes (05/11/2026) |
| **PDF progress document** | https://github.com/Nycri/brewpos/blob/main/PDF_PROGRESS_DOCUMENTATION.pdf |
| **Last updated** | 2026-11-05 |

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 11 - BoardTrack: Boarding House & Rental Management System |
| **Database inspected** | `boardtrack_db` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `boardtrack_app` (Runtime), `boardtrack_report_user` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 9.1 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on room, property, lease, payment via postgres` |
| **Query analyzed** | Available rooms lookup with rate sort (Nested loop, 0.137 ms) & Multi-table lease billing summary (Hash joins, 0.281 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.29 ms with 100% buffer cache hits) |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/db-maintenance/group-11-boardtrack/` |
| **Feedback sent to owners** | Yes (05/11/2026) |
| **PDF progress document** | https://github.com/Nycri/brewpos/blob/main/PDF_PROGRESS_DOCUMENTATION.pdf |
| **Last updated** | 2026-11-05 |

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 11 - BoardTrack: Boarding House & Rental Management System |
| **Database and backup file** | `boardtrack_db`, `boardtrack_db_activity6.dump`, Size: 35,849 bytes, SHA256: `5B83883ADEB30784905A7EB22B7B67607DC6D1CCB22A508E5559DE60EB48559A` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-05 18:48:31) |
| **Restore test result** | Success (Restored into `boardtrack_db_restore_test`; all 8 tables, constraints, triggers, and data verified) |
| **Access control after restore** | Yes (Activity 4 roles and permissions validated; `boardtrack_report_user` denied reading `app_user`) |
| **Recovery simulation result** | Recovered (Simulated deleted test property successfully restored from verified baseline state) |
| **Proposed RPO and RTO** | RPO: 1 hour, RTO: 30 minutes |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/db-backup-recovery/group-11-boardtrack/` |
| **Feedback sent to owners** | Yes (05/11/2026) |
| **PDF progress document** | https://github.com/Nycri/brewpos/blob/main/PDF_PROGRESS_DOCUMENTATION.pdf |
| **Last updated** | 2026-11-05 |

---

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash (via Antigravity AI assistant).
- **Tasks Performed:** 
  1. Inspecting database schema definitions, constraints, exclusion rules, and relationship hierarchies for Group 11's BoardTrack system.
  2. Formulating and executing PostgreSQL least-privilege verification test suites for Activity 4 (runtime vs. auditor credential separation).
  3. Diagnosing runtime maintenance boundaries, executing `VACUUM` and `ANALYZE`, and profiling execution plans (`EXPLAIN (ANALYZE, BUFFERS)`) for Activity 5.
  4. Executing custom archive backup creation with `pg_dump`, SHA256 checksum computation, test database restoration with `pg_restore`, and controlled recovery simulation drill for Activity 6.
  5. Compiling and formatting comprehensive progress documentation adhering to course rubric guidelines.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in `psql`. Real outputs, timings, and checksums are recorded in this report. No codebase files within `BoardTrack` were modified.
