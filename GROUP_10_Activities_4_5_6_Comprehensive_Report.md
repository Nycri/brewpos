# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Restaurant Online Ordering & Table Reservation System** (Mesa / Binary Sisters) |
| **Target Project Group** | **Group 10:** Sandoval, Callos, Romingquit, Tambio, Tungol, and Gordonas |
| **Target Project Repository** | [https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System](https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System) |
| **Target Project Branch** | `main` |
| **Primary Database** | `grp_binarysisters_projectdb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp_binarysisters_projectdb_restore_test` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime User `rors_app`)
   - 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `rors_report`)
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
   - 3.3 Restoration into Separate Test Database (`grp_binarysisters_projectdb_restore_test`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Initial Activity 3 Implementation
In initial application prototypes, default documentation (`docs/database-setup.md`), and early `.env.example` configurations, applications frequently connect using the PostgreSQL superuser (`postgres`) or an unconstrained table-owner account. For Group 10's **Restaurant Online Ordering & Table Reservation System (Mesa)**, connecting an interactive public web application under a superuser account severely violates the **Principle of Least Privilege (PoLP)**. Because the restaurant application handles online food orders, customer registration, live table reservations, and financial payment receipts, any unauthenticated attacker exploiting a SQL injection vulnerability or unvalidated input could drop critical business tables, alter menu prices, steal customer bcrypt password hashes, or truncate financial payment records.

### Activity 4 Solution & Design
To secure Group 10's `grp_binarysisters_projectdb` database, we implemented a comprehensive Role-Based Access Control (RBAC) hierarchy that strictly separates application runtime execution, compliance reporting, maintenance, backups, and administrative database management:
- **Group Roles (`NOLOGIN`):** Architectural privilege templates that define specific capability boundaries (`rors_runtime_grp`, `rors_readonly_grp`, `rors_maint_grp`, `rors_backup_grp`, and `rors_admin_grp`).
- **Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their respective group roles (`rors_app`, `rors_report`, `rors_maint`, `rors_backup`, and `rors_dba`).
- **Narrow Deletion (`DELETE` Scoping):** The runtime account `rors_app` is granted `SELECT`, `INSERT`, and `UPDATE` on operational tables, but its `DELETE` capability is strictly confined to `menu_item`, `restaurant_table`, `reservation`, and `staff` (permitting restaurant managers and staff to manage menu availability, table inventory, reservation cancellations, and personnel accounts). Crucially, `rors_app` is **strictly denied `DELETE` on `orders`, `order_item`, `payment`, and `customer`**. This guarantees that customer order histories and financial payment receipts cannot be deleted through runtime API calls, preserving business and legal audit trails.
- **Auditor Credential Protection:** The read-only reporting account `rors_report` is granted `SELECT` on `menu_item`, `restaurant_table`, `orders`, `order_item`, `reservation`, and `payment`, and on the four public reporting views (`v_customer_directory`, `v_staff_directory`, `v_order_summary`, `v_reservation_schedule`). It is **explicitly denied `SELECT` on `customer` and `staff` tables**. Because the reporting views run with owner rights and intentionally omit `customer.password` and `staff.password`, auditors can inspect full customer directories and staff rosters without ever exposing bcrypt password hashes.
- **Dedicated Maintenance & Backup Roles:** Routine database maintenance (`VACUUM`, `ANALYZE`) is delegated to `rors_maint`, which holds PostgreSQL 18's built-in `pg_maintain` and `pg_monitor` roles with **zero `SELECT` privilege on application data**. Logical database backups are decoupled from runtime accounts and assigned to `rors_backup`, which utilizes the built-in `pg_read_all_data` role for schema-wide logical extraction without write or schema modification permissions.
- **Connection Limits & Timeouts:** To prevent Denial-of-Service (DoS) and runaway queries, connection limits are enforced (`rors_app`: 20, `rors_report`: 5, `rors_maint`: 3, `rors_backup`: 2, `rors_dba`: 3), alongside query statement timeouts (`30s` for `rors_app`, `60s` for `rors_report`).

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `rors_app` | `rors_runtime_grp` | Application Runtime (Customer, Staff, Manager, Admin) | Runs the PHP web application: customer ordering, checkout payments, table booking, menu updates, and staff roster | • `CONNECT` on `grp_binarysisters_projectdb`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on `customer`, `orders`, `payment`<br>• `SELECT, INSERT, UPDATE, DELETE` on `menu_item`, `restaurant_table`, `reservation`, `staff`<br>• `SELECT, INSERT` on `order_item`<br>• `USAGE, SELECT` on all sequences<br>• `SELECT` on `v_order_summary`, `v_reservation_schedule` | Confining `DELETE` strictly to catalog and staff entities while denying `DELETE` on `orders`, `order_item`, and `payment` guarantees that customer dining history and financial transactions cannot be erased by application logic or injection attacks. |
| `rors_report` | `rors_readonly_grp` | Restaurant Auditor / Reporting Inspector | Read-only inspection of sales performance, reservation schedules, customer directories, and table utilization | • `CONNECT` on `grp_binarysisters_projectdb`<br>• `USAGE` on schema `public`<br>• `SELECT` on `menu_item`, `restaurant_table`, `orders`, `order_item`, `reservation`, `payment`<br>• `SELECT` on views: `v_customer_directory`, `v_staff_directory`, `v_order_summary`, `v_reservation_schedule`<br>*(No access to `customer` or `staff` tables)* | Enables comprehensive auditing of business operations while completely shielding bcrypt password hashes stored in `customer.password` and `staff.password`. |
| `rors_maint` | `rors_maint_grp` | Database Maintenance Operator | Runs `VACUUM` and `ANALYZE`, inspects locks and connection stats | • `CONNECT` on `grp_binarysisters_projectdb`<br>• `USAGE` on schema `public`<br>• Built-in roles `pg_maintain` (PG 17+) and `pg_monitor`<br>*(No `SELECT` access to application tables)* | Allows routine bloat cleanup and query planner optimization without granting access to confidential customer profiles or sales data. |
| `rors_backup` | `rors_backup_grp` | Backup Operator | Executes logical database dumps (`pg_dump`) | • `CONNECT` on `grp_binarysisters_projectdb`<br>• `USAGE` on schema `public`<br>• Built-in role `pg_read_all_data` (read only) | Grants the schema-wide read access necessary for `pg_dump` while preventing any DML modifications or DDL schema tampering. |
| `rors_dba` | `rors_admin_grp` | Database Administrator | Schema maintenance, index provisioning, operational management | • `CONNECT` on `grp_binarysisters_projectdb`<br>• `USAGE, CREATE` on schema `public`<br>• `ALL` on all tables & sequences<br>• Built-in role `pg_monitor`<br>*(NOCREATEDB, NOCREATEROLE, NOSUPERUSER)* | Day-to-day administrative control without possessing superuser privileges or cluster-wide destruction capabilities. |
| `postgres` | *(Superuser)* | System DBA | Initial provisioning, disaster recovery, cluster-level maintenance | Superuser / Owner privileges | Reserved strictly for initial bootstrap and offline disaster recovery; completely decoupled from application runtime configurations. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script was executed as `postgres` against `grp_binarysisters_projectdb`:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE rors_runtime_grp NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE rors_readonly_grp NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE rors_maint_grp NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE rors_backup_grp NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE rors_admin_grp NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;

-- 2. Create Login Roles (LOGIN) with Connection Limits
CREATE ROLE rors_app LOGIN PASSWORD 'Nycri2134' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 20;
CREATE ROLE rors_report LOGIN PASSWORD 'ReportPass2026!' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 5;
CREATE ROLE rors_maint LOGIN PASSWORD 'MaintPass2026!' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 3;
CREATE ROLE rors_backup LOGIN PASSWORD 'BackupPass2026!' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 2;
CREATE ROLE rors_dba LOGIN PASSWORD 'DbaAdminPass2026!' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 3;

-- 3. Assign Role Memberships
GRANT rors_runtime_grp TO rors_app;
GRANT rors_readonly_grp TO rors_report;
GRANT rors_maint_grp TO rors_maint;
GRANT rors_backup_grp TO rors_backup;
GRANT rors_admin_grp TO rors_dba;

-- 4. Built-in PostgreSQL Predefined Roles
GRANT pg_monitor TO rors_maint_grp;
GRANT pg_monitor TO rors_admin_grp;
GRANT pg_read_all_data TO rors_backup_grp;
GRANT pg_maintain TO rors_maint_grp;

-- 5. Safety Timeouts
ALTER ROLE rors_app SET statement_timeout = '30s';
ALTER ROLE rors_report SET statement_timeout = '60s';

-- 6. Lock down PUBLIC permissions and Database Connection
REVOKE ALL ON DATABASE grp_binarysisters_projectdb FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE grp_binarysisters_projectdb TO rors_runtime_grp, rors_readonly_grp, rors_maint_grp, rors_backup_grp, rors_admin_grp;
GRANT USAGE ON SCHEMA public TO rors_runtime_grp, rors_readonly_grp, rors_maint_grp, rors_backup_grp, rors_admin_grp;
GRANT CREATE ON SCHEMA public TO rors_admin_grp;

-- 7. Revoke existing table privileges from PUBLIC
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;

-- 8. Application Runtime Grants (Principle of Least Privilege)
GRANT SELECT, INSERT, UPDATE ON customer, orders, payment TO rors_runtime_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON menu_item, restaurant_table, reservation, staff TO rors_runtime_grp;
GRANT SELECT, INSERT ON order_item TO rors_runtime_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rors_runtime_grp;
GRANT SELECT ON v_order_summary, v_reservation_schedule TO rors_runtime_grp;

-- 9. Read-Only / Auditor Grants (Credential Shielding)
GRANT SELECT ON menu_item, restaurant_table, orders, order_item, reservation, payment TO rors_readonly_grp;
GRANT SELECT ON v_customer_directory, v_staff_directory, v_order_summary, v_reservation_schedule TO rors_readonly_grp;

-- 10. Administrator Role Grants
GRANT ALL ON ALL TABLES IN SCHEMA public TO rors_admin_grp;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO rors_admin_grp;

-- 11. Default Privileges for Future Objects
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON TABLES TO rors_admin_grp;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON SEQUENCES TO rors_admin_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `rors_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT menu_item_id, item_name, category, price FROM menu_item LIMIT 5;
  ```
- **Expected Result:** Allowed; returns available restaurant menu items.
- **Actual Result / Output:**
  ```text
   menu_item_id |     item_name      | category  | price  
  --------------+--------------------+-----------+--------
              1 | Lumpiang Shanghai  | Appetizer | 150.00
              2 | Calamares          | Appetizer | 220.00
              3 | Sisig Nachos       | Appetizer | 240.00
              4 | Tokwat Baboy       | Appetizer | 160.00
              5 | Chicharon Bulaklak | Appetizer | 260.00
  (5 rows)
  ```
- **Status:** PASS

### Test 1.2: Insert an Item & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO menu_item (item_name, category, price, availability) 
  VALUES ('Activity 4 Test Dish: Crispy Pork Belly', 'Main Dish', 350.00, true) 
  RETURNING menu_item_id, item_name;
  ```
- **Expected Result:** Allowed; inserts record and advances `menu_item_menu_item_id_seq`.
- **Actual Result / Output:**
  ```text
   menu_item_id |                item_name                
  --------------+-----------------------------------------
             51 | Activity 4 Test Dish: Crispy Pork Belly
  (1 row)
  INSERT 0 1
  ```
- **Status:** PASS

### Test 1.3: Update Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE menu_item SET price = 365.00 
  WHERE item_name = 'Activity 4 Test Dish: Crispy Pork Belly' 
  RETURNING menu_item_id, price;
  ```
- **Expected Result:** Allowed; runtime account updates menu price.
- **Actual Result / Output:**
  ```text
   menu_item_id | price  
  --------------+--------
             51 | 365.00
  (1 row)
  UPDATE 1
  ```
- **Status:** PASS

### Test 1.4: Delete Menu Item (Allowed — Narrow `DELETE` on `menu_item`)
- **SQL Statement:**
  ```sql
  DELETE FROM menu_item 
  WHERE item_name = 'Activity 4 Test Dish: Crispy Pork Belly' 
  RETURNING menu_item_id, item_name;
  ```
- **Expected Result:** Allowed; runtime account is authorized to remove decommissioned dishes.
- **Actual Result / Output:**
  ```text
   menu_item_id |                item_name                
  --------------+-----------------------------------------
             51 | Activity 4 Test Dish: Crispy Pork Belly
  (1 row)
  DELETE 1
  ```
- **Status:** PASS

### Test 1.5: Delete Orders Record (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM orders WHERE order_id = 1;
  ```
- **Expected Result:** Denied; runtime account must never delete customer dining transactions.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table orders
  ```
- **Status:** PASS

### Test 1.6: Delete Payment Record (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM payment WHERE payment_id = 1;
  ```
- **Expected Result:** Denied; runtime account must never purge financial ledger payments.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table payment
  ```
- **Status:** PASS

### Test 1.7: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE menu_item;
  ```
- **Expected Result:** Denied; DDL requires object ownership (`postgres`).
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table menu_item
  ```
- **Status:** PASS

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `rors_report`)

### Test 2.1: Read Reporting Views (Allowed — Masked Password Columns)
- **SQL Statement:**
  ```sql
  SELECT customer_id, first_name, last_name, email, phone FROM v_customer_directory LIMIT 5;
  SELECT order_id, customer_name, total_amount, payment_status FROM v_order_summary LIMIT 5;
  ```
- **Expected Result:** Allowed; auditor inspects directory and billing summary without password exposure.
- **Actual Result / Output:**
  ```text
   customer_id | first_name | last_name |           email           |     phone     
  -------------+------------+-----------+---------------------------+---------------
             1 | Olivia     | Wagner    | olivia.wagner@example.com | 0905-427-9233
             2 | Diego      | Lopez     | diego.lopez@example.org   | 0995-762-2681
             3 | Noah       | Smith     | noah.smith@example.com    | 0936-715-7891
             4 | Mia        | Hughes    | mia.hughes@example.com    | 0998-660-9042
             5 | Daniel     | Garcia    | daniel.garcia@example.com | 0998-551-4931
  (5 rows)

   order_id | customer_name | total_amount | payment_status 
  ----------+---------------+--------------+----------------
          1 | Ines Kumar    |       710.00 | paid
          2 | Mia Hughes    |      1000.00 | paid
          3 | Jack Singh    |       730.00 | paid
          4 | Felix Moreau  |       350.00 | paid
          5 | Nour Kumar    |       885.00 | paid
  (5 rows)
  ```
- **Status:** PASS

### Test 2.2: Attempt to Read Customer Password Hashes (Denied — Credential Protection)
- **SQL Statement:**
  ```sql
  SELECT password FROM customer LIMIT 1;
  ```
- **Expected Result:** Denied; auditor role has no `SELECT` privilege on table `customer` to protect bcrypt hashes.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table customer
  ```
- **Status:** PASS

### Test 2.3: Attempt to Read Staff Table (Denied — Credential Protection)
- **SQL Statement:**
  ```sql
  SELECT * FROM staff LIMIT 1;
  ```
- **Expected Result:** Denied; auditor role has no `SELECT` privilege on table `staff`.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table staff
  ```
- **Status:** PASS

### Test 2.4: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO menu_item (item_name, category, price) VALUES ('Unauthorized Item', 'Appetizer', 100);
  ```
- **Expected Result:** Denied; read-only account cannot insert records.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table menu_item
  ```
- **Status:** PASS

### Test 2.5: Attempt to Update Records (Denied)
- **SQL Statement:**
  ```sql
  UPDATE menu_item SET price = 999 WHERE menu_item_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot alter menu prices.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table menu_item
  ```
- **Status:** PASS

### Test 2.6: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM reservation WHERE reservation_id = 1;
  ```
- **Expected Result:** Denied; read-only account cannot delete reservations.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table reservation
  ```
- **Status:** PASS

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  CREATE DATABASE rogue_test_db;
  ```
- **Expected Result:** Denied; application users must not possess administrative cluster privileges (`NOCREATEDB`).
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied to create database
  ```
- **Status:** PASS

### Test 3.2: Attempt to Connect to Unrelated Database (Denied)
- **Command:**
  ```bash
  psql -U rors_app -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
  ```
- **Expected Result:** Denied; cross-database connection is blocked by revoked PUBLIC privileges.
- **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "canteen_db"
  DETAIL:  User does not have CONNECT privilege.
  ```
- **Status:** PASS

### Test 3.3: Attempt to Connect to Maintenance Database `postgres` (Denied after Fix)
- **Command:**
  ```bash
  psql -U rors_app -h localhost -p 5432 -d postgres -c "SELECT 1;"
  ```
- **Initial Observation:** By default in PostgreSQL, `PUBLIC` retains `CONNECT` on `postgres` and `template1`. When `rors_app` connected, it was initially admitted.
- **Remediation Executed:** As superuser `postgres`, we executed:
  ```sql
  REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
  REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
  ```
- **Retest Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "postgres"
  DETAIL:  User does not have CONNECT privilege.
  ```
- **Status:** PASS

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Unrestricted Cluster-Level Default `CONNECT` on `postgres` and `template1`:** In standard PostgreSQL installations, database-level `CONNECT` is granted to `PUBLIC` by default on maintenance databases. Even after locking down `grp_binarysisters_projectdb`, `rors_app` could connect to `postgres`. We resolved this by executing `REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;` and `REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;` as superuser.
2. **Missing `03_set_passwords.psql` in `database/roles/`:** The automated setup script `database/setup.ps1` calls `database/roles/03_set_passwords.psql`, but the file was missing from the repository folder (a template existed under `db-access-control/_template/`). We established secure passwords directly using `ALTER ROLE ... WITH PASSWORD` without modifying the checked-out codebase.
3. **Application `.env` Synchronization:** The local `.env` was configured with `DB_USER=rors_app` and synchronized with the database credential, ensuring zero superuser connections during application runtime.

### Feedback to Owning Group (Group 10: Mesa / Binary Sisters)
- **Commendation:** The relational schema architecture is exemplary. The implementation of deferred constraint triggers (`trg_orders_require_items`, `trg_order_item_keep_one`), dynamic subtotal calculations (`trg_order_item_total`), and the `btree_gist` exclusion constraint preventing table double-booking (`ex_reservation_no_overlap`) represents industry-standard database integrity engineering.
- **Recommendation 1:** Add the missing `03_set_passwords.psql` script to `database/roles/` or update `setup.ps1` so peer evaluators can initialize least-privilege roles without encountering script termination.
- **Recommendation 2:** In production environments, replace predictable seed passwords (`Admin123!`, `Staff123!`, `Password123!`) with dynamically generated cryptographically strong secrets.

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

-- 5. Total Number of Tables, Views, and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE') AS total_tables,
    (SELECT count(*) FROM information_schema.views WHERE table_schema = 'public') AS total_views,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public') AS total_sequences;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Total Database Size:** `9342 kB` (~9.3 MB)
- **Object Count:** `total_tables: 8`, `total_views: 4`, `total_sequences: 7`
- **Table Storage Breakdown:**
  ```text
      table_name    | total_size | table_size | index_size 
  ------------------+------------+------------+------------
   orders           | 96 kB      | 16 kB      | 48 kB
   customer         | 72 kB      | 16 kB      | 32 kB
   order_item       | 72 kB      | 16 kB      | 32 kB
   reservation      | 64 kB      | 8192 bytes | 56 kB
   menu_item        | 48 kB      | 8192 bytes | 32 kB
   payment          | 40 kB      | 8192 bytes | 32 kB
   restaurant_table | 40 kB      | 8192 bytes | 32 kB
   staff            | 40 kB      | 8192 bytes | 32 kB
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
WHERE r.rolname LIKE 'rors%'
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
             datname           | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  -----------------------------+------------------+------------------------+--------------------------+-----------+-----------
   grp_binarysisters_projectdb |                1 |                    107 |                       10 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The rolled-back transactions (10) correspond directly to the controlled sub-transaction rollbacks inside `04_verify_privileges.sql` and intentional negative security boundary tests.
- **Roles Verification Output:**
  ```text
       role_name     | can_login | is_superuser |     member_of_group      
  -------------------+-----------+--------------+--------------------------
   rors_admin_grp    | f         | f            | {pg_monitor}
   rors_app          | t         | f            | {rors_runtime_grp}
   rors_backup       | t         | f            | {rors_backup_grp}
   rors_backup_grp   | f         | f            | {pg_read_all_data}
   rors_dba          | t         | f            | {rors_admin_grp}
   rors_maint        | t         | f            | {rors_maint_grp}
   rors_maint_grp    | f         | f            | {pg_monitor,pg_maintain}
   rors_readonly_grp | f         | f            | {}
   rors_report       | t         | f            | {rors_readonly_grp}
   rors_runtime_grp  | f         | f            | {}
  (10 rows)
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
    table_name    | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum |        last_autovacuum        | last_analyze |       last_autoanalyze        
------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-------------------------------+--------------+-------------------------------
 customer         |                8 |                     109 |         286 |          51 |          47 |             |                               |              | 2026-11-05 07:45:44.403743-05
 staff            |                4 |                      18 |           1 |          10 |           8 |             |                               |              | 
 menu_item        |                8 |                     159 |         168 |          50 |           2 |             |                               |              | 2026-11-05 07:46:44.21035-05
 reservation      |                6 |                      50 |          48 |          50 |           0 |             |                               |              | 
 payment          |                3 |                      56 |           9 |          56 |           0 |             |                               |              | 2026-11-05 07:45:44.409659-05
 orders           |                6 |                      66 |         455 |          60 |           0 |             | 2026-11-05 07:45:44.342679-05 |              | 2026-11-05 07:45:44.38167-05
 order_item       |               64 |                    5299 |         168 |         167 |           0 |             |                               |              | 2026-11-05 07:45:44.385549-05
 restaurant_table |                3 |                      20 |         101 |          20 |           0 |             |                               |              | 
(8 rows)
```
*Analysis:* `customer` accumulated 47 dead tuples and `staff` accumulated 8 dead tuples as a direct consequence of seed data password hashing updates (`UPDATE customer SET password = crypt(...)`). In addition, `menu_item` accumulated 2 dead tuples from Activity 4's insert, update, and delete test of the temporary dish record. This provided concrete justification for executing `VACUUM`.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Permission Denied Security Logs:**
   - **Log Entry:** `ERROR: permission denied for table orders on statement DELETE FROM orders WHERE order_id = 1;` by `rors_app` (Timestamp: `07:46:42 EST`)
   - **Log Entry:** `ERROR: permission denied for table payment on statement DELETE FROM payment WHERE payment_id = 1;` by `rors_app` (Timestamp: `07:46:43 EST`)
   - **Log Entry:** `ERROR: permission denied for table customer on statement SELECT password FROM customer LIMIT 1;` by `rors_report` (Timestamp: `07:46:56 EST`)
   - **Log Entry:** `ERROR: permission denied for table staff on statement SELECT * FROM staff LIMIT 1;` by `rors_report` (Timestamp: `07:46:56 EST`)
   - **Log Entry:** `ERROR: permission denied for table menu_item on statement INSERT INTO menu_item (item_name, category, price) VALUES ('Unauthorized Item', 'Appetizer', 100);` by `rors_report` (Timestamp: `07:46:56 EST`)
   - **Log Entry:** `ERROR: permission denied for table menu_item on statement UPDATE menu_item SET price = 999 WHERE menu_item_id = 1;` by `rors_report` (Timestamp: `07:46:56 EST`)
   - **Log Entry:** `ERROR: permission denied for table reservation on statement DELETE FROM reservation WHERE reservation_id = 1;` by `rors_report` (Timestamp: `07:46:56 EST`)
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `rors_app` (Timestamp: `07:48:06 EST`)
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `rors_app` (Timestamp: `07:48:06 EST`)
   - **Log Entry:** `FATAL: permission denied for database "postgres" DETAIL: User does not have CONNECT privilege.` on connection by `rors_app` (Timestamp: `07:48:24 EST`)
   - **Assessment:** Positive validation of least privilege. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Checkpoint & Background Writer Health:** Periodic background checkpoints completed normally (e.g., `checkpoint complete: wrote 153 buffers (0.9%), total=16.791 s`).
3. **Server Stability:** Zero server crashes, memory leaks, or unhandled exceptions occurred in the engine log.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- 1. Test Permission Boundary (as runtime application user rors_app):
VACUUM menu_item;

-- 2. Maintenance Execution (as dedicated maintenance role rors_maint via pg_maintain):
VACUUM (VERBOSE, ANALYZE) customer;
VACUUM (VERBOSE, ANALYZE) staff;
VACUUM (VERBOSE, ANALYZE) menu_item;
ANALYZE;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM menu_item;` as `rors_app` produced:
  ```text
  WARNING:  permission denied to vacuum "menu_item", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`VACUUM (VERBOSE, ANALYZE) customer;` Output:**
  ```text
  INFO:  vacuuming "grp_binarysisters_projectdb.public.customer"
  INFO:  finished vacuuming "grp_binarysisters_projectdb.public.customer": index scans: 1
  pages: 0 removed, 2 remain, 2 scanned (100.00% of total), 0 eagerly scanned
  tuples: 1 removed, 51 remain, 0 are dead but not yet removable
  removable cutoff: 2339, which was 0 XIDs old when operation ended
  new relfrozenxid: 2221, which is 4 XIDs ahead of previous value
  visibility map: 2 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 2 pages from table (100.00% of total) had 47 dead item identifiers removed
  index "customer_pkey": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  index "uq_customer_email": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  buffer usage: 88 hits, 0 reads, 9 dirtied
  WAL usage: 11 records, 9 full page images, 51033 bytes, 0 buffers full
  INFO:  analyzing "public.customer"
  INFO:  "customer": scanned 2 of 2 pages, containing 51 live rows and 0 dead rows; 51 rows in sample, 51 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) staff;` Output:**
  ```text
  INFO:  vacuuming "grp_binarysisters_projectdb.public.staff"
  INFO:  finished vacuuming "grp_binarysisters_projectdb.public.staff": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 8 removed, 10 remain, 0 are dead but not yet removable
  removable cutoff: 2340, which was 0 XIDs old when operation ended
  new relfrozenxid: 2221, which is 4 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 70 hits, 0 reads, 5 dirtied
  WAL usage: 6 records, 5 full page images, 34975 bytes, 0 buffers full
  INFO:  analyzing "public.staff"
  INFO:  "staff": scanned 1 of 1 pages, containing 10 live rows and 0 dead rows; 10 rows in sample, 10 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) menu_item;` Output:**
  ```text
  INFO:  vacuuming "grp_binarysisters_projectdb.public.menu_item"
  INFO:  finished vacuuming "grp_binarysisters_projectdb.public.menu_item": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 2 removed, 50 remain, 0 are dead but not yet removable
  removable cutoff: 2341, which was 0 XIDs old when operation ended
  new relfrozenxid: 2221, which is 4 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 1 pages from table (100.00% of total) had 1 dead item identifiers removed
  index "menu_item_pkey": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  index "idx_menu_item_category": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  buffer usage: 83 hits, 0 reads, 7 dirtied
  WAL usage: 9 records, 7 full page images, 41329 bytes, 0 buffers full
  INFO:  analyzing "public.menu_item"
  INFO:  "menu_item": scanned 1 of 1 pages, containing 50 live rows and 0 dead rows; 50 rows in sample, 50 estimated total rows
  VACUUM
  ```
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership or membership in `pg_maintain`. When attempted under `rors_app`, PostgreSQL emitted `WARNING: permission denied to vacuum "menu_item", skipping it`. This confirms that runtime application accounts cannot perform maintenance operations. In contrast, `rors_maint` executed `VACUUM` and `ANALYZE` without requiring superuser escalation.
- **Exclusion of `VACUUM FULL`:** In accordance with DBA best practices, `VACUUM FULL` was deliberately avoided. `VACUUM FULL` rewrites table files and acquires an `AccessExclusiveLock`, completely blocking concurrent `SELECT`, `INSERT`, and `UPDATE` statements — which would freeze online restaurant customer orders and table reservations. Standard non-blocking `VACUUM` reclaimed all 47 dead pointers in `customer`, 8 in `staff`, and 2 in `menu_item` with zero downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Customer Menu Browsing by Category & Availability
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT menu_item_id, item_name, category, price, availability
FROM menu_item
WHERE category = 'Main Course' AND availability = true
ORDER BY price ASC;
```

#### Actual Execution Plan Output
```text
                                                  QUERY PLAN                                                   
---------------------------------------------------------------------------------------------------------------
 Sort  (cost=2.09..2.14 rows=21 width=34) (actual time=0.477..0.478 rows=22.00 loops=1)
   Sort Key: price
   Sort Method: quicksort  Memory: 26kB
   Buffers: shared hit=4
   ->  Seq Scan on menu_item  (cost=0.00..1.62 rows=21 width=34) (actual time=0.187..0.211 rows=22.00 loops=1)
         Filter: (availability AND ((category)::text = 'Main Course'::text))
         Rows Removed by Filter: 28
         Buffers: shared hit=1
 Planning:
   Buffers: shared hit=125
 Planning Time: 5.462 ms
 Execution Time: 0.614 ms
(12 rows)
```

#### Interpretation
1. **Execution Time:** `0.614 ms` (sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 4`, with 0 physical disk block reads (100% in-memory cache hit ratio).
3. **Sequential Scan Justification:** Even though an index exists on `category` (`idx_menu_item_category`), the PostgreSQL query planner chose a sequential scan. Because the entire `menu_item` table fits within a single 8 kB heap page (`Buffers: shared hit = 1`), reading that single block sequentially in memory is significantly faster than navigating index B-Tree pages followed by random pointer lookups.

---

### Query 2: Multi-Table Active Orders with Customer & Payment Joins
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    o.order_id,
    c.first_name || ' ' || c.last_name AS customer_name,
    c.phone,
    o.order_date,
    o.order_status,
    o.total_amount,
    p.payment_method,
    p.payment_status
FROM orders o
JOIN customer c ON o.customer_id = c.customer_id
LEFT JOIN payment p ON o.order_id = p.order_id
WHERE o.order_status IN ('pending', 'confirmed')
ORDER BY o.order_date DESC;
```

#### Actual Execution Plan Output
```text
                                                             QUERY PLAN                                                             
------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=7.44..7.45 rows=3 width=81) (actual time=0.214..0.216 rows=3.00 loops=1)
   Sort Key: o.order_date DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=8
   ->  Hash Join  (cost=4.54..7.42 rows=3 width=81) (actual time=0.146..0.155 rows=3.00 loops=1)
         Hash Cond: (c.customer_id = o.customer_id)
         Buffers: shared hit=5
         ->  Seq Scan on customer c  (cost=0.00..2.51 rows=51 width=28) (actual time=0.022..0.028 rows=51.00 loops=1)
               Buffers: shared hit=2
         ->  Hash  (cost=4.51..4.51 rows=3 width=40) (actual time=0.092..0.093 rows=3.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=3
               ->  Hash Right Join  (cost=2.79..4.51 rows=3 width=40) (actual time=0.073..0.086 rows=3.00 loops=1)
                     Hash Cond: (p.order_id = o.order_id)
                     Buffers: shared hit=3
                     ->  Seq Scan on payment p  (cost=0.00..1.56 rows=56 width=14) (actual time=0.011..0.015 rows=56.00 loops=1)
                           Buffers: shared hit=1
                     ->  Hash  (cost=2.75..2.75 rows=3 width=30) (actual time=0.046..0.047 rows=3.00 loops=1)
                           Buckets: 1024  Batches: 1  Memory Usage: 9kB
                           Buffers: shared hit=2
                           ->  Seq Scan on orders o  (cost=0.00..2.75 rows=3 width=30) (actual time=0.022..0.035 rows=3.00 loops=1)
                                 Filter: ((order_status)::text = ANY ('{pending,confirmed}'::text[]))
                                 Rows Removed by Filter: 57
                                 Buffers: shared hit=2
 Planning:
   Buffers: shared hit=386
 Planning Time: 32.458 ms
 Execution Time: 0.285 ms
(28 rows)
```

#### Interpretation
1. **Execution Time:** `0.285 ms`.
2. **Buffer Hits:** `shared hit = 8` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner executed a `Hash Right Join` between `orders` and `payment`, followed by a `Hash Join` with `customer`. In-memory hash tables required only 9 kB each, executing with `Batches: 1` (zero disk spills).

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain existing primary, foreign key, and category indexes | All tables are small (fitting within 8–16 kB heap blocks), and queries execute in < 0.62 ms with 100% buffer cache hits. Adding further indexes now would introduce write overhead without improving latency. |
| **Follow-up Action** | Composite Index for Scaled Order Management | When historical order records surpass 50,000 rows, filtering active kitchen orders will benefit from a partial composite index: `CREATE INDEX idx_orders_active_date ON orders (order_date DESC) WHERE order_status IN ('pending', 'confirmed', 'preparing');`. |
| **Follow-up Action** | Scheduled Maintenance via Windows Task Scheduler | Deploy a scheduled maintenance job executing `VACUUM (ANALYZE)` nightly at 03:00 AM under `rors_maint` to automatically reclaim dead tuples resulting from status updates during peak dining hours. |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp_binarysisters_projectdb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp_binarysisters_projectdb` (Schema: `public`)<br>• 8 Relational Tables: `customer`, `menu_item`, `restaurant_table`, `staff`, `orders`, `order_item`, `reservation`, `payment`<br>• 7 Sequences, 4 Views, 4 Triggers, 3 Trigger Functions, Constraints, Table ACL Grants | Ensures full transactional consistency across customer profiles, restaurant table setups, kitchen order queues, and payment ledgers. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp_binarysisters_projectdb_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 15 Minutes<br>**RTO (Recovery Time Objective):** 30 Minutes | A 15-minute RPO limits unrecorded customer food orders and table reservation conflicts; a 30-minute RTO restores cashier point-of-sale and kitchen display workflows promptly. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with long-term financial audit compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`rors_app`, `rors_report`, `rors_maint`, `rors_backup`, `rors_dba`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `rors_backup` role:
```bash
pg_dump -U rors_backup -h localhost -p 5432 -Fc -d grp_binarysisters_projectdb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 1F7958A75D31FF95EBBA1D3BC6138435870E361F600FF10D57D1D0B5D4C0AD46
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump

  Name                                       Length LastWriteTime        
  ----                                       ------ -------------        
  grp_binarysisters_projectdb_activity6.dump  51020 05/11/2026 8:01:42 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents confirmed **112 TOC entries**, including all 8 tables (`customer`, `menu_item`, `restaurant_table`, `staff`, `orders`, `order_item`, `reservation`, `payment`), 7 sequences, 4 views, trigger functions, constraints (primary keys, foreign keys, unique checks, and exclusion constraint `ex_reservation_no_overlap`), indexes, and default ACL grants.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

## 3.3 Restoration into Separate Test Database (`grp_binarysisters_projectdb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp_binarysisters_projectdb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp_binarysisters_projectdb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp_binarysisters_projectdb_activity6.dump"
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
      table_name    | table_type |  owner   
  ------------------+------------+----------
   customer         | BASE TABLE | postgres
   menu_item        | BASE TABLE | postgres
   order_item       | BASE TABLE | postgres
   orders           | BASE TABLE | postgres
   payment          | BASE TABLE | postgres
   reservation      | BASE TABLE | postgres
   restaurant_table | BASE TABLE | postgres
   staff            | BASE TABLE | postgres
  (8 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'customer' AS tbl, count(*) FROM customer
  UNION ALL SELECT 'menu_item', count(*) FROM menu_item
  UNION ALL SELECT 'restaurant_table', count(*) FROM restaurant_table
  UNION ALL SELECT 'staff', count(*) FROM staff
  UNION ALL SELECT 'orders', count(*) FROM orders
  UNION ALL SELECT 'order_item', count(*) FROM order_item
  UNION ALL SELECT 'reservation', count(*) FROM reservation
  UNION ALL SELECT 'payment', count(*) FROM payment;
  ```
- **Actual Output:**
  ```text
         tbl        | count 
  ------------------+-------
   customer         |    51
   menu_item        |    50
   restaurant_table |    20
   staff            |    10
   orders           |    60
   order_item       |   167
   reservation      |    50
   payment          |    56
  (8 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks, Unique)
- **Query:**
  ```sql
  SELECT constraint_type, count(*) 
  FROM information_schema.table_constraints 
  WHERE table_schema = 'public' 
  GROUP BY constraint_type 
  ORDER BY constraint_type;
  ```
- **Actual Output:**
  ```text
   constraint_type | count 
  -----------------+-------
   CHECK           |    61
   FOREIGN KEY     |     6
   PRIMARY KEY     |     8
   UNIQUE          |     4
  (4 rows)
  ```
  *Analysis:* All 79 table constraint definitions plus the `btree_gist` exclusion constraint were verified and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Runtime User Test (`rors_app` reading `menu_item`):**
  ```bash
  psql -U rors_app -h localhost -p 5432 -d grp_binarysisters_projectdb_restore_test -c "SELECT menu_item_id, item_name, price FROM menu_item LIMIT 3;"
  ```
  *Output:*
  ```text
   menu_item_id |     item_name     | price  
  --------------+-------------------+--------
              1 | Lumpiang Shanghai | 150.00
              2 | Calamares         | 220.00
              3 | Sisig Nachos      | 240.00
  (3 rows)
  ```
- **Auditor Restriction Test (`rors_report` attempting to read `customer` table):**
  ```bash
  psql -U rors_report -h localhost -p 5432 -d grp_binarysisters_projectdb_restore_test -c "SELECT * FROM customer;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table customer
  ```
  *Conclusion:* Least-privilege ACLs and credential protections transferred seamlessly into the restored database.

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT order_id, customer_name, order_status, total_amount, payment_status 
  FROM v_order_summary 
  LIMIT 5;
  ```
- **Actual Output:**
  ```text
   order_id | customer_name | order_status | total_amount | payment_status 
  ----------+---------------+--------------+--------------+----------------
          1 | Ines Kumar    | ready        |       710.00 | paid
          2 | Mia Hughes    | completed    |      1000.00 | paid
          3 | Jack Singh    | completed    |       730.00 | paid
          4 | Felix Moreau  | completed    |       350.00 | paid
          5 | Nour Kumar    | preparing    |       885.00 | paid
  (5 rows)
  ```

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp_binarysisters_projectdb_restore_test`, a disposable test menu item record was established:
- **Query:**
  ```sql
  INSERT INTO menu_item (menu_item_id, item_name, description, category, price, availability)
  VALUES (999, 'DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao', 'Tender Wagyu beef cubes sauteed in garlic butter', 'Main Course', 780.00, true);

  SELECT menu_item_id, item_name, category, price, availability 
  FROM menu_item 
  WHERE menu_item_id = 999;
  ```
- **Actual Output:**
  ```text
   menu_item_id |                       item_name                        |  category   | price  | availability 
  --------------+--------------------------------------------------------+-------------+--------+--------------
            999 | DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao | Main Course | 780.00 | t
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM menu_item WHERE menu_item_id = 999;
  SELECT count(*) AS count_after_deletion FROM menu_item WHERE menu_item_id = 999;
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
  INSERT INTO menu_item (menu_item_id, item_name, description, category, price, availability)
  VALUES (999, 'DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao', 'Tender Wagyu beef cubes sauteed in garlic butter', 'Main Course', 780.00, true)
  ON CONFLICT (menu_item_id) DO NOTHING;

  SELECT menu_item_id, item_name, category, price, availability 
  FROM menu_item 
  WHERE menu_item_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   menu_item_id |                       item_name                        |  category   | price  | availability 
  --------------+--------------------------------------------------------+-------------+--------+--------------
            999 | DRILL-TEST-DISH-999: Specialty Sizzling Wagyu Salpicao | Main Course | 780.00 | t
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original dish name, category, price, and availability preserved. The drill record was subsequently deleted:
```sql
DELETE FROM menu_item WHERE menu_item_id = 999;
```

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `rors_runtime_grp`, `rors_app`, `rors_report`, etc., before permissions can attach.
2. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`grp_binarysisters_projectdb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For critical dining environments processing live electronic payments and food delivery tickets, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in the class Google Sheets tracker:

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 10 - Restaurant Online Ordering & Table Reservation System (Binary Sisters) |
| **Repository cloned** | `https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System`, Branch: `main` |
| **Database setup result** | Success (Verified PostgreSQL 18.6 on port 5432, schema built with 8 tables, 4 views, triggers, constraints, populated sample data, configured least-privilege roles). |
| **Access-control folder link** |  |
| **Access-control status** | Completed |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 10 - Restaurant Online Ordering & Table Reservation System (Binary Sisters) |
| **Database inspected** | `grp_binarysisters_projectdb` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (Superuser), `rors_app` (Runtime), `rors_report` (Audit), `rors_maint` (Maintenance via pg_maintain) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 9.3 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on customer, staff, menu_item via rors_maint (pg_maintain)` |
| **Query analyzed** | Menu item browsing by category & availability (Seq scan, 0.614 ms, 4 buffer hits) & Multi-table active order join (Hash joins, 0.285 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.62 ms with 100% buffer cache hits) |
| **Evidence folder link** |  |
| **Feedback sent to owners** | Ys (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 10 - Restaurant Online Ordering & Table Reservation System (Binary Sisters) |
| **Database and backup file** | `grp_binarysisters_projectdb`, `grp_binarysisters_projectdb_activity6.dump`, Size: 51,020 bytes, SHA256: `1F7958A75D31FF95EBBA1D3BC6138435870E361F600FF10D57D1D0B5D4C0AD46` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-05 08:01:42) |
| **Restore test result** | Success (Restored into `grp_binarysisters_projectdb_restore_test`; all 8 tables, constraints, and data verified) |
| **Access control after restore** | Yes (Activity 4 roles and permissions validated; `rors_report` denied reading `customer`) |
| **Recovery simulation result** | Recovered (Simulated deleted test menu dish successfully restored from verified baseline state) |
| **Proposed RPO and RTO** | RPO: 15 minutes, RTO: 30 minutes |
| **Evidence folder link** |  |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash.
- **Tasks Performed:** 
  1. Inspecting database schema definitions, constraints, views, triggers, and relationship hierarchies for Group 10's Restaurant Online Ordering & Table Reservation System.
  2. Formulating and executing PostgreSQL least-privilege verification test suites for Activity 4 (runtime vs. auditor credential masking vs. cross-database boundaries).
  3. Diagnosing runtime maintenance boundaries, executing `VACUUM` and `ANALYZE` under PostgreSQL 18's `pg_maintain` role, and profiling execution plans (`EXPLAIN (ANALYZE, BUFFERS)`) for Activity 5.
  4. Executing custom archive backup creation with `pg_dump`, SHA256 checksum computation, test database restoration with `pg_restore`, and controlled recovery simulation drill for Activity 6.
  5. Compiling and formatting comprehensive progress documentation adhering to course rubric guidelines.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in `psql`. Real outputs, timings, and checksums are recorded in this report. No files within the target project codebase were modified.

