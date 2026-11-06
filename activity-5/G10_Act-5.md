# COMP 018: Database Administration - Activity 5: Database Maintenance and Monitoring
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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

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
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/activity-5/Evidence-act5` |
| **Feedback sent to owners** | Ys (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---

> [!NOTE]
> All technical diagnostic outputs, execution plan buffers, log snippets, and vacuum records for Group 10 are archived in [Evidence-act5/G10_Act-5_Evidence.md](Evidence-act5/G10_Act-5_Evidence.md).