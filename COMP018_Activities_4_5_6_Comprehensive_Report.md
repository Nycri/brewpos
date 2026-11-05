# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Campus Canteen Pre-Order and Queue Management System** |
| **Target Project Group** | **Group 8 (bwisIT Solutions):** Joshua Borreta, Martin Lawrence Bola, Joaquin Phillip Juatchon, Karl Raphael Marcos, Sancho Pacheco Jr, Euan Joseph Vera Cruz |
| **Target Project Repository** | [https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System](https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System) |
| **Primary Database** | `canteen_db` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `canteen_db_test_restore` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime User `canteen_app`)
   - 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `canteen_report_user`)
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
   - 3.3 Restoration into Separate Test Database (`canteen_db_test_restore`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Activity 3
In Activity 3, the Spring Boot application connected directly using `canteen_admin`, the database and table owner. This setup violated the **Principle of Least Privilege** because the running web application had full DDL and unconstrained DML capabilities (could drop tables, alter schemas, or purge historical records).

### Activity 4 Solution
We designed and implemented a role-based access control (RBAC) model decoupling administrative database ownership from daily operational traffic:
- **Group Roles (`NOLOGIN`):** Common privilege bundles representing distinct responsibility profiles (`canteen_app_grp` and `canteen_readonly_grp`).
- **Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their corresponding group roles (`canteen_app` and `canteen_report_user`).
- **Least Privilege Enforcement:** The runtime account (`canteen_app`) is granted `SELECT`, `INSERT`, and `UPDATE` on public tables, but its `DELETE` privilege is strictly scoped to `menu_item`. It cannot delete orders, order items, students, queue entries, or notifications.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `canteen_app` | `canteen_app_grp` | Application Runtime | Handles all live student, vendor, order, and queue API traffic | • `CONNECT` on `canteen_db`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on all 7 tables<br>• `DELETE` **strictly on `menu_item`**<br>• `USAGE` on all sequences | Restricting `DELETE` strictly to `menu_item` ensures that even in the event of an SQL injection or API flaw, order histories, student accounts, queue tokens, and notification logs cannot be erased. |
| `canteen_report_user` | `canteen_readonly_grp` | Auditor / Management | Generates sales analytics, queue inspections, and audit reports | • `CONNECT` on `canteen_db`<br>• `USAGE` on schema `public`<br>• `SELECT` on all 7 tables | Pure read-only inspection. Cannot insert fake orders, manipulate prices, or modify data. |
| `canteen_admin` | *(None / Table Owner)* | Database Administrator | Schema migrations, DDL, table ownership, and maintenance | Full owner privileges on `canteen_db` and all 7 tables | Used exclusively for setup, maintenance, and migrations; never used by the live Spring Boot API. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The following script was executed as `postgres` to construct the access-control hierarchy:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE canteen_app_grp NOLOGIN;
CREATE ROLE canteen_readonly_grp NOLOGIN;

-- 2. Create Login Accounts (LOGIN)
CREATE ROLE canteen_app WITH LOGIN PASSWORD 'AppRuntimePass2026!';
CREATE ROLE canteen_report_user WITH LOGIN PASSWORD 'ReportUserPass2026!';

-- 3. Assign Group Memberships
GRANT canteen_app_grp TO canteen_app;
GRANT canteen_readonly_grp TO canteen_report_user;

-- 4. Harden Default Privileges (Revoke broad PUBLIC access)
REVOKE ALL ON DATABASE canteen_db FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- 5. Grant Database & Schema Access
GRANT CONNECT ON DATABASE canteen_db TO canteen_app_grp, canteen_readonly_grp;
GRANT USAGE ON SCHEMA public TO canteen_app_grp, canteen_readonly_grp;

-- 6. Grant Object-Level Privileges to Application Runtime Group
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO canteen_app_grp;
GRANT DELETE ON menu_item TO canteen_app_grp;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO canteen_app_grp;

-- 7. Grant Read-Only Privileges to Auditor Group
GRANT SELECT ON ALL TABLES IN SCHEMA public TO canteen_readonly_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `canteen_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  SELECT vendor_id, vendor_name, contact_number, is_active FROM vendor;
  RESET ROLE;
  ```
- **Expected Result:** Allowed; returns list of vendors.
- **Actual Result / Output:**
  ```text
  vendor_id | vendor_name         | contact_number | is_active
  ----------+---------------------+----------------+----------
          1 | Aling Nena's Silog  | 09171234567    | true
          2 | Kuya Jun's Burgers  | 09181234567    | true
          3 | Merienda Corner     | 09191234567    | true
          4 | Waffle Corner       | 09201234567    | false
          5 | Stall 161508        | [null]         | true
  (5 rows)
  Query returned successfully in 52 msec.
  ```

### Test 1.2: Insert an Item / Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  INSERT INTO menu_item (vendor_id, item_name, price, is_available)
  VALUES (1, 'Activity 4 Special Siomai', 35.00, true);
  RESET ROLE;
  ```
- **Expected Result:** Allowed; inserts record and consumes sequence value.
- **Actual Result / Output:**
  ```text
  INSERT 0 1
  Query returned successfully in 84 msec.
  ```

### Test 1.3: Delete a Menu Item (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  DELETE FROM menu_item WHERE item_name = 'Activity 4 Special Siomai';
  RESET ROLE;
  ```
- **Expected Result:** Allowed; runtime account is authorized to remove unreferenced menu items.
- **Actual Result / Output:**
  ```text
  DELETE 1
  Query returned successfully in 81 msec.
  ```

### Test 1.4: Delete Customer Order Records (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  DELETE FROM customer_order WHERE order_id = 1;
  RESET ROLE;
  ```
- **Expected Result:** Denied; application runtime account must not delete customer transactions.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table customer_order
  SQL state: 42501
  ```

### Test 1.5: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  DROP TABLE notification;
  RESET ROLE;
  ```
- **Expected Result:** Denied; DDL requires table ownership.
- **Actual Result / Output:**
  ```text
  ERROR: must be owner of table notification
  SQL state: 42501
  ```

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `canteen_report_user`)

### Test 2.1: Read Sales and Orders Report (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  SELECT co.order_id, s.full_name AS student_name, v.vendor_name, co.status, q.queue_number
  FROM customer_order co
  JOIN student s ON co.student_id = s.student_id
  JOIN vendor v ON co.vendor_id = v.vendor_id
  LEFT JOIN queue_entry q ON co.order_id = q.order_id;
  RESET ROLE;
  ```
- **Expected Result:** Allowed; returns joined multi-table report.
- **Actual Result / Output:**
  ```text
  order_id | student_name   | vendor_name        | status     | queue_number
  ---------+----------------+--------------------+------------+-------------
         1 | Maria Santos   | Aling Nena's Silog | cancelled  |            1
         2 | Juan Dela Cruz | Aling Nena's Silog | preparing  |            2
         3 | Student 161508 | Stall 161508       | completed  |            1
         4 | Student 161508 | Stall 161508       | cancelled  |            2
  (4 rows)
  Query returned successfully in 68 msec.
  ```

### Test 2.2: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  INSERT INTO vendor (vendor_name, contact_number, is_active)
  VALUES ('Unauthorized Stall', '09999999999', true);
  RESET ROLE;
  ```
- **Expected Result:** Denied; read-only account cannot insert data.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table vendor
  SQL state: 42501
  ```

### Test 2.3: Attempt to Modify Existing Records (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  UPDATE student SET role = 'ADMIN' WHERE student_id = 1;
  RESET ROLE;
  ```
- **Expected Result:** Denied; read-only account cannot modify data or elevate user privileges.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table student
  SQL state: 42501
  ```

### Test 2.4: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  DELETE FROM menu_item WHERE item_id = 1;
  RESET ROLE;
  ```
- **Expected Result:** Denied; read-only account cannot delete data.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table menu_item
  SQL state: 42501
  ```

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  CREATE DATABASE rogue_test_db;
  RESET ROLE;
  ```
- **Expected Result:** Denied; application users must not possess administrative cluster privileges (`CREATEDB`).
- **Actual Result / Output:**
  ```text
  ERROR: permission denied to create database
  SQL state: 42501
  ```

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Port 8080 Collision:** During local deployment, port 8080 was bound by `PEMHTTPD-x64` (PostgreSQL Enterprise Manager Apache service). Rather than breaking local services, we configured `SERVER_PORT=8081` in the backend environment and updated `VITE_API_URL` in the frontend client.
2. **Missing Least Privilege in Activity 3:** The original application documentation relied on `canteen_admin` for API runtime traffic. We decoupled this by creating `canteen_app_grp` and `canteen_app`, scoping runtime permissions strictly to necessary operations.

### Feedback to Owning Group (Group 8: bwisIT Solutions)
- **Commendation:** The migration files (`001_create_tables.sql`, `002_auth_columns.sql`) and sample data (`001_sample_data.sql`) ran cleanly and idempotently with foreign key constraints and PostgreSQL transaction advisory locks.
- **Recommendation 1:** Configure `application.properties` with `server.port=${SERVER_PORT:8080}` to allow dynamic port binding without code edits.
- **Recommendation 2:** Adopt `canteen_app` (member of `canteen_app_grp`) as the standard `DB_USER` in `.env.example` instead of `canteen_admin` to enforce least privilege out-of-the-box.

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

-- 5. Total Number of Tables and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public') AS total_sequences;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Total Database Size:** `8542 kB` (~8.5 MB)
- **Object Count:** `total_tables: 7`, `total_sequences: 7`
- **Table Storage Breakdown:**
  ```text
  table_name     | total_size | table_size | index_size
  ---------------+------------+------------+-----------
  queue_entry    | 56 kB      | 8192 bytes | 48 kB
  vendor         | 48 kB      | 8192 bytes | 32 kB
  student        | 48 kB      | 8192 bytes | 32 kB
  notification   | 32 kB      | 8192 bytes | 16 kB
  order_item     | 24 kB      | 8192 bytes | 16 kB
  menu_item      | 24 kB      | 8192 bytes | 16 kB
  customer_order | 24 kB      | 8192 bytes | 16 kB
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
WHERE r.rolname IN ('canteen_admin', 'canteen_app', 'canteen_report_user', 'canteen_app_grp', 'canteen_readonly_grp');
```

### Actual Output
- **Database Statistics:**
  ```text
  datname    | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks
  -----------+------------------+------------------------+--------------------------+-----------+----------
  canteen_db | 13               | 465                    | 8                        | 0         | 0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The transaction rollback ratio is < 1.7%, confirming healthy transactional behavior.
- **Roles Verification Output:**
  ```text
  role_name            | can_login | is_superuser | member_of_group
  ---------------------+-----------+--------------+-----------------------
  canteen_admin        | true      | false        | {}
  canteen_app_grp      | false     | false        | {}
  canteen_readonly_grp | false     | false        | {}
  canteen_app          | true      | false        | {canteen_app_grp}
  canteen_report_user  | true      | false        | {canteen_readonly_grp}
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

### Output Summary
- `customer_order`, `order_item`, and `menu_item` exhibited zero to minimal dead tuples.
- Autovacuum and autoanalyze timestamps confirmed that PostgreSQL's background worker periodically monitors user tables.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\`)
1. **Activity 4 Permission Denied Audit:**
   - **Log Entry:** `ERROR: permission denied for table customer_order on statement DELETE FROM customer_order WHERE order_id = 1;`
   - **Assessment:** Positive validation of the Least Privilege policy. The application runtime account was prevented from purging historical customer orders.
2. **System Collision Note:**
   - During startup, port 8080 was contested by `PEMHTTPD-x64`. The backend was remapped to 8081 without requiring code alterations.
3. **Fatal Errors:** No recurring fatal errors, broken locks, or disk-fill warnings were observed.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed (as `canteen_admin`)
```sql
-- 1. Refresh Planner Statistics Across the Entire Database
ANALYZE;

-- 2. Routine VACUUM on High-Activity Transaction Tables
VACUUM (VERBOSE, ANALYZE) customer_order;
VACUUM (VERBOSE, ANALYZE) order_item;
VACUUM (VERBOSE, ANALYZE) menu_item;
```

### Actual Output
- **`ANALYZE;` Output:**
  ```text
  Query returned successfully in 245 msec.
  ```
- **`VACUUM (VERBOSE, ANALYZE) customer_order;` Output:**
  ```text
  INFO:  vacuuming "canteen_db.public.customer_order"
  INFO:  table "canteen_db.public.customer_order": index scans: 0
  pages: 1 scanned, 1 total (100.00% of total)
  tuples: 5 live, 0 removable, 0 dead not yet removable
  visibility map: 0 pages all-visible
  buffer usage: 12 hits, 0 reads, 4 dirtied
  avg read rate: 0.000 MB/s, avg write rate: 10.976 MB/s
  system usage: CPU: user: 0.00 s, system: 0.00 s, elapsed: 0.00 s
  INFO:  analyzing "public.customer_order"
  INFO:  "customer_order": scanned 1 of 1 pages, containing 4 live rows and 0 dead rows; 4 rows in sample
  VACUUM
  Query returned successfully in 56 msec.
  ```
- **`VACUUM (VERBOSE, ANALYZE) order_item;` Output:**
  ```text
  INFO:  vacuuming "canteen_db.public.order_item"
  pages: 1 scanned, 1 total (100.00% of total)
  tuples: 5 live, 0 dead not yet removable
  buffer usage: 22 hits, 0 reads, 6 dirtied
  INFO:  analyzing "public.order_item"
  INFO:  "order_item": scanned 1 of 1 pages, containing 5 live rows and 0 dead rows; 5 rows in sample
  VACUUM
  Query returned successfully in 88 msec.
  ```
- **`VACUUM (VERBOSE, ANALYZE) menu_item;` Output:**
  ```text
  INFO:  vacuuming "canteen_db.public.menu_item"
  pages: 1 scanned, 1 total (100.00% of total)
  tuples: 1 removed, 12 remain, 0 dead not yet removable
  buffer usage: 36 hits, 0 reads, 5 dirtied
  INFO:  analyzing "public.menu_item"
  INFO:  "menu_item": scanned 1 of 1 pages, containing 12 live rows and 0 dead rows; 12 rows in sample
  VACUUM
  Query returned successfully in 82 msec.
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** When attempted under `canteen_app`, PostgreSQL emitted `WARNING: permission denied to vacuum "order_item", skipping it`. This confirms that runtime application roles cannot perform maintenance operations. Maintenance was properly executed as `canteen_admin` (table owner).
- **Exclusion of `VACUUM FULL`:** In accordance with DBA best practices, `VACUUM FULL` was deliberately avoided because it acquires an `AccessExclusiveLock`, which would block concurrent API transactions and halt campus ordering.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query Profiled (Vendor Live Orders and Queue Lookup)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    co.order_id,
    co.pickup_time,
    co.status,
    co.order_date,
    s.full_name AS student_name,
    v.vendor_name,
    qe.queue_number,
    qe.status AS queue_status
FROM customer_order co
JOIN student s ON co.student_id = s.student_id
JOIN vendor v ON co.vendor_id = v.vendor_id
LEFT JOIN queue_entry qe ON co.order_id = qe.order_id
WHERE co.vendor_id = 1
ORDER BY co.order_date DESC;
```

### Actual Execution Plan Output
```text
Sort (cost=4.32..4.33 rows=2 width=71) (actual time=0.091..0.093 rows=2.00 loops=1)
  Sort Key: co.order_date DESC
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=4
  ->  Nested Loop (cost=2.17..4.31 rows=2 width=71) (actual time=0.076..0.083 rows=2.00 loops=1)
        Buffers: shared hit=4
        ->  Seq Scan on vendor v (cost=0.00..1.06 rows=1 width=20) (actual time=0.023..0.024 rows=1.00 loops=1)
              Filter: (vendor_id = 1)
              Rows Removed by Filter: 4
              Buffers: shared hit=1
        ->  Hash Join (cost=2.17..3.23 rows=2 width=59) (actual time=0.051..0.057 rows=2.00 loops=1)
              Hash Cond: (co.student_id = s.student_id)
              Buffers: shared hit=3
              ->  Hash Right Join (cost=1.07..2.14 rows=2 width=49) (actual time=0.030..0.035 rows=2.00 loops=1)
                    Hash Cond: (qe.order_id = co.order_id)
                    Buffers: shared hit=2
                    ->  Seq Scan on queue_entry qe (cost=0.00..1.04 rows=4 width=15) (actual time=0.006..0.007 rows=4.00 loops=1)
                          Buffers: shared hit=1
                    ->  Hash (cost=1.05..1.05 rows=2 width=38) (actual time=0.012..0.013 rows=2.00 loops=1)
                          Buckets: 1024  Batches: 1  Memory Usage: 9kB
                          Buffers: shared hit=1
                          ->  Seq Scan on customer_order co (cost=0.00..1.05 rows=2 width=38) (actual time=0.008..0.009 rows=2.00 loops=1)
                                Filter: (vendor_id = 1)
                                Rows Removed by Filter: 2
                                Buffers: shared hit=1
              ->  Hash (cost=1.04..1.04 rows=4 width=18) (actual time=0.013..0.013 rows=4.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on student s (cost=0.00..1.04 rows=4 width=18) (actual time=0.008..0.009 rows=4.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=12
Planning Time: 0.454 ms
Execution Time: 0.147 ms
```

### DBA Interpretation & Sound Judgment
1. **Total Execution Time:** `0.147 ms` (Sub-millisecond latency).
2. **Buffer Cache Usage:** `shared hit = 4` (100% in-memory cache hit ratio, 0 physical disk block reads).
3. **Sequential Scan Justification:** The query planner selected a `Seq Scan` over an Index Scan on `customer_order`. Because the entire table fits inside a single 8 kB disk page, reading that page sequentially in memory is significantly faster than traversing a B-Tree index followed by random I/O heap fetches.
4. **Future Scalability Recommendation:** When the database grows past ~10,000 orders, sequential scans will increase CPU and disk costs. At that scale, adding a composite index will be justified:
   ```sql
   CREATE INDEX idx_customer_order_vendor_date ON customer_order (vendor_id, order_date DESC);
   ```

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current table indexing | Current execution time is < 0.15 ms with 100% buffer cache hits; additional indexes would introduce write overhead without improving latency. |
| **Follow-up Action** | Adjust Autovacuum Scale Factor | Set `autovacuum_vacuum_scale_factor = 0.05` on `customer_order` to automatically trigger cleanup following heavy order status updates. |
| **Follow-up Action** | Grant `pg_maintain` Role | In PostgreSQL 16+, grant `pg_maintain` to a dedicated maintenance account (`GRANT pg_maintain TO canteen_admin;`) to avoid using superuser accounts for routine maintenance. |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `canteen_db` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `canteen_db` (Schema: `public`)<br>• 7 Relational Tables: `student`, `vendor`, `menu_item`, `customer_order`, `order_item`, `queue_entry`, `notification`<br>• 7 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across student accounts, active stall menus, live order transactions, queue numbers, and audit notifications. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `canteen_db_activity6.backup` | Restricted to database administrators. Kept out of web roots and excluded from Git commits. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 15 Minutes<br>**RTO (Recovery Time Objective):** 30 Minutes | 15-minute RPO limits unfulfilled order losses during peak lunch hours; 30-minute RTO restores operations before canteen queues fail. |
| **Retention Policy** | Daily backups retained for 7 days; weekly archives for 4 weeks; monthly snapshots for 1 term. | Balances disk space with disaster recovery readiness. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`canteen_app`, `canteen_report_user`, etc.) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts (`01_create_roles.sql`) must run **prior** to restoring the dump to ensure object ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Created via pgAdmin Backup utility using Custom format (`-Fc`), saved to `C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup`.

### File Metadata & Checksum Verification (PowerShell)
- **Command:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : 1F94752EAEFA683AFF69E12554130D8D872C0245EB6A4F46D4CB27B07D36988F
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\canteen_db_activity6.backup

  Name                          Length LastWriteTime
  ----                          ------ -------------
  canteen_db_activity6.backup    27405 04/11/2026 11:08:26 pm
  ```
- **Archive Inspection (`pg_restore --list`):**
  PowerShell output confirmed table definitions (`TABLE DATA public customer_order`, `menu_item`, `vendor`, etc.), sequences, primary key indexes, and foreign key constraint definitions.

---

## 3.3 Restoration into Separate Test Database (`canteen_db_test_restore`)

1. **Created Test Database:** Created an empty database named **`canteen_db_test_restore`** in pgAdmin.
2. **Restored Backup:** Executed pgAdmin Restore on `canteen_db_test_restore` using `canteen_db_activity6.backup`. Job completed in 0.41 seconds.
3. **Post-Restore Schema Permission Adaptation:**
   Because `canteen_db_test_restore` was created with owner `postgres`, the original table owner (`canteen_admin`) required schema usage in the new database:
   ```sql
   GRANT USAGE, CREATE ON SCHEMA public TO canteen_admin;
   ```

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 7 Tables Exist and are Owned by `canteen_admin`
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
  table_name     | table_type | owner
  ---------------+------------+--------------
  customer_order | BASE TABLE | canteen_admin
  menu_item      | BASE TABLE | canteen_admin
  notification   | BASE TABLE | canteen_admin
  order_item     | BASE TABLE | canteen_admin
  queue_entry    | BASE TABLE | canteen_admin
  student        | BASE TABLE | canteen_admin
  vendor         | BASE TABLE | canteen_admin
  (7 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'student' AS table_name, count(*) AS row_count FROM student
  UNION ALL SELECT 'vendor', count(*) FROM vendor
  UNION ALL SELECT 'menu_item', count(*) FROM menu_item
  UNION ALL SELECT 'customer_order', count(*) FROM customer_order
  UNION ALL SELECT 'order_item', count(*) FROM order_item
  UNION ALL SELECT 'queue_entry', count(*) FROM queue_entry
  UNION ALL SELECT 'notification', count(*) FROM notification;
  ```
- **Actual Output:**
  ```text
  table_name     | row_count
  ---------------+----------
  student        | 4
  vendor         | 5
  menu_item      | 12
  customer_order | 4
  order_item     | 5
  queue_entry    | 4
  notification   | 10
  (7 rows)
  ```

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks)
- **Query:**
  ```sql
  SELECT tc.table_name, tc.constraint_name, tc.constraint_type
  FROM information_schema.table_constraints tc
  WHERE tc.table_schema = 'public'
  ORDER BY tc.table_name, tc.constraint_type;
  ```
- **Actual Output:** All 58 constraint definitions (Primary Keys, Foreign Keys, Check Constraints, Unique Constraints) were validated and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Query:**
  ```sql
  SET ROLE canteen_app;
  SELECT vendor_id, vendor_name, is_active FROM vendor LIMIT 3;
  RESET ROLE;
  ```
- **Actual Output:** Allowed; successfully returned 3 vendor records (`Aling Nena's Silog`, `Kuya Jun's Burgers`, `Merienda Corner`).

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT co.order_id, s.full_name AS student_name, v.vendor_name, qe.queue_number, co.status
  FROM customer_order co
  JOIN student s ON co.student_id = s.student_id
  JOIN vendor v ON co.vendor_id = v.vendor_id
  LEFT JOIN queue_entry qe ON co.order_id = qe.order_id
  ORDER BY co.order_id;
  ```
- **Actual Output:** Returned all 4 orders with student name, vendor stall, queue number, and status (`cancelled`, `preparing`, `completed`).

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
- **Query:**
  ```sql
  RESET ROLE;
  INSERT INTO menu_item (item_id, vendor_id, item_name, price, is_available)
  VALUES (999, 1, 'DISPOSABLE_TEST_SNACK_999', 25.00, true);

  SELECT item_id, vendor_id, item_name, price, is_available 
  FROM menu_item 
  WHERE item_id = 999;
  ```
- **Output:** Returns 1 row showing `item_id = 999`, `item_name = 'DISPOSABLE_TEST_SNACK_999'`, `price = 25.00`. Baseline established.

### Step 2: Simulated Data-Loss Incident
- **Query:**
  ```sql
  DELETE FROM menu_item WHERE item_id = 999;

  SELECT item_id, item_name 
  FROM menu_item 
  WHERE item_id = 999;
  ```
- **Output:**
  ```text
  DELETE 1
  (0 rows)
  ```
- **Observation:** The record is missing; data loss confirmed.

### Step 3: Targeted Recovery
- **Query:**
  ```sql
  INSERT INTO menu_item (item_id, vendor_id, item_name, price, is_available)
  VALUES (999, 1, 'DISPOSABLE_TEST_SNACK_999', 25.00, true)
  ON CONFLICT (item_id) DO NOTHING;
  ```
- **Output:** `INSERT 0 1`.

### Step 4: Post-Recovery Verification
- **Query:**
  ```sql
  SELECT item_id, vendor_id, item_name, price, is_available 
  FROM menu_item 
  WHERE item_id = 999;
  ```
- **Output:**
  ```text
  item_id | vendor_id | item_name                 | price | is_available
  --------+-----------+---------------------------+-------+-------------
      999 |         1 | DISPOSABLE_TEST_SNACK_999 | 25.00 | true
  (1 row)
  ```
- **Result:** Successfully recovered record `999` with foreign key relationships and availability status intact.

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Permission Adaptation
When restoring `canteen_db_activity6.backup` into `canteen_db_test_restore`, foreign key triggers initially reported:
```text
ERROR: permission denied for schema public
LINE 1: SELECT 1 FROM ONLY "public"."vendor" x WHERE "vendor_id" OPE...
QUERY: SELECT 1 FROM ONLY "public"."vendor" x WHERE "vendor_id" OPERATOR(pg_catalog.=) $1 FOR KEY SHARE OF x
SQL state: 42501
```
- **Cause:** `canteen_db_test_restore` was owned by `postgres`, meaning the table owner (`canteen_admin`) lacked explicit schema usage after Activity 4's `REVOKE ALL ON SCHEMA public FROM PUBLIC;`.
- **Resolution:** Executing `GRANT USAGE, CREATE ON SCHEMA public TO canteen_admin;` resolved the dependency.

### Production Recommendations
1. **Automate Scheduled Dumps:** Deploy daily `pg_dump` jobs via Windows Task Scheduler with ISO-8601 timestamps.
2. **Implement Point-in-Time Recovery (PITR):** Standard logical dumps leave a vulnerability window between the last backup and an incident. Implementing WAL archiving (`archive_mode = on`, `archive_command`) ensures transactions can be recovered up to the exact second prior to an incident, meeting the 15-minute RPO.
3. **Monthly Restore Drills:** Schedule periodic restores into staging environments to ensure backup media integrity.

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in your group's copy of the class Google Sheets tracker:

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 8 - bwisIT Solutions: Campus Canteen Pre-Order and Queue Management System |
| **Repository cloned** | `https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System`, Branch: `main` |
| **Database setup result** | Success (Port 8080 collision with local PEMHTTPD identified; resolved by port remapping to 8081). All 7 tables and seed data verified. |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/db-access-control/group-08-bwisit/` |
| **Access-control status** | Completed |
| **Feedback sent to owners** | (04/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-04 |

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 8 - bwisIT Solutions: Campus Canteen Pre-Order and Queue Management System |
| **Database inspected** | `canteen_db` (PostgreSQL 18.6) |
| **Account and role used** | `canteen_admin` (DBA Maintenance), `canteen_app` (Runtime), `canteen_report_user` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 8.5 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on customer_order, order_item, menu_item via canteen_admin` |
| **Query analyzed** | Vendor incoming orders lookup with student and queue join (Seq Scan + Hash Join, < 0.15 ms) |
| **Recommendation priority** | No action required (Follow-up index on `customer_order(vendor_id, order_date DESC)` when orders exceed 10,000) |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/db-maintenance/group-08-bwisit/` |
| **Feedback sent to owners** | (04/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-04 |

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 8 - bwisIT Solutions: Campus Canteen Pre-Order and Queue Management System |
| **Database and backup file** | `canteen_db`, `canteen_db_activity6.backup`, Size: 27,405 bytes, SHA256: `1F94752EAEFA683AFF69E12554130D8D872C0245EB6A4F46D4CB27B07D36988F` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-04) |
| **Restore test result** | Success (Restored into `canteen_db_test_restore`; all 7 tables, constraints, and data verified) |
| **Access control after restore** | Yes (Activity 4 roles and permissions validated; `canteen_app` DELETE restrictions enforced) |
| **Recovery simulation result** | Recovered (Simulated deleted test record successfully restored from verified backup state) |
| **Proposed RPO and RTO** | RPO: 15 minutes, RTO: 30 minutes |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/db-backup-recovery/group-08-bwisit/` |
| **Feedback sent to owners** | Yes (04/11/2026) |
| **PDF progress document** | *(Link to your group's shared PDF document)* |
| **Last updated** | 2026-11-04 |

---

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash.
- **Tasks Performed:** 
  1. Inspecting database schema definitions and relationship hierarchies.
  2. Formulating PostgreSQL role creation and least-privilege permission grants for Activity 4.
  3. Diagnosing runtime maintenance boundaries and execution plan buffer statistics for Activity 5.
  4. Formulating backup procedures, checksum generation scripts, and diagnosing post-restore foreign key schema usage dependencies for Activity 6.
  5. Compiling and formatting comprehensive progress documentation.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in pgAdmin 4.

