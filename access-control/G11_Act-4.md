# COMP 018: Database Administration â€” Activity 4: Access Control Report
## Group Evaluation Report: Group 11 â€” BoardTrack: Boarding House & Rental Management System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **BoardTrack: Boarding House & Rental Management System** |
| **Target Project Group** | **Group 11:** Barcarse, Adrian; Cataluña, Zyruz Gabriel; Camu, Edgar; Policarpio, Kurt; Crucillo, Karl Robert |
| **Target Project Repository** | [https://github.com/karlrobertc-art/BoardTrack](https://github.com/karlrobertc-art/BoardTrack) |
| **Target Project Branch** | `main` |
| **Primary Database** | `boardtrack_db` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `boardtrack_db_restore_test` |

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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

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

---
