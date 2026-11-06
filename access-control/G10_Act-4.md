# COMP 018: Database Administration - Activity 4: Access Control Report
## Group Evaluation Report: Group 10 - Restaurant Online Ordering & Table Reservation System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Restaurant Online Ordering & Table Reservation System** (Mesa / Binary Sisters) |
| **Target Project Group** | **Group 10:** Sandoval, Cellyn; Callos, Kathlen Mae; Romingquit, Arika; Tambio, Jenny Mae; Tungol, Jaimie; Gordonas, John Benedick |
| **Target Project Repository** | [https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System](https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System) |
| **Target Project Branch** | `main` |
| **Primary Database** | `grp_binarysisters_projectdb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp_binarysisters_projectdb_restore_test` |

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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 10 - Restaurant Online Ordering & Table Reservation System (Binary Sisters) |
| **Repository cloned** | `https://github.com/kathlenmaecallos11-arch/Restaurant-Online-Ordering-Table-Reservation-System`, Branch: `main` |
| **Database setup result** | Success (Verified PostgreSQL 18.6 on port 5432, schema built with 8 tables, 4 views, triggers, constraints, populated sample data, configured least-privilege roles). |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/access-control` |
| **Access-control status** | Completed |
| **Feedback sent to owners** |  (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---
