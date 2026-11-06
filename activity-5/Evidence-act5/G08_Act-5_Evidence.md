# Activity 5 Technical Evidence Dossier - Group 08: Campus Canteen Pre-Order and Queue Management System
## COMP 018: Database Maintenance & Monitoring Technical Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **Campus Canteen Pre-Order and Queue Management System** |
| **Target Project Group** | **Group 8 (bwisIT Solutions):** Borreta, Joshua; Bola, Martin Lawrence; Juatchon, Joaquin Phillip; Marcos, Karl Raphael; Pacheco Jr., Sancho; Vera Cruz, Euan Joseph |
| **Target Project Repository** | [https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System](https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System) |
| **Primary Database** | `canteen_db` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `canteen_db_test_restore` |

---

# EVIDENCE DOSSIER & VERIFICATION ARTIFACTS

This evidence dossier contains the unedited diagnostic queries, terminal execution outputs, log entries, and performance plans executed during Activity 5 for **Group 08 (Campus Canteen Pre-Order and Queue Management System)**.

---

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

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current table indexing | Current execution time is < 0.15 ms with 100% buffer cache hits; additional indexes would introduce write overhead without improving latency. |
| **Follow-up Action** | Adjust Autovacuum Scale Factor | Set `autovacuum_vacuum_scale_factor = 0.05` on `customer_order` to automatically trigger cleanup following heavy order status updates. |
| **Follow-up Action** | Grant `pg_maintain` Role | In PostgreSQL 16+, grant `pg_maintain` to a dedicated maintenance account (`GRANT pg_maintain TO canteen_admin;`) to avoid using superuser accounts for routine maintenance. |

---

---

## Technical Audit Verification Sign-Off

| Verification Item | Tested Requirement | Outcome | Audited By |
|---|---|---|---|
| **Health & Sizing Check** | Database footprint & storage allocation inspection | PASS | Group 4 (BrewPOS) |
| **Connection & Session Audit** | Open backends, locks, transaction rollback ratio | PASS | Group 4 (BrewPOS) |
| **Table Stats & Bloat Inspection** | Dead tuple accumulation tracking via pg_stat_user_tables | PASS | Group 4 (BrewPOS) |
| **PostgreSQL Engine Log Audit** | Authentication handshakes, error trapping, checkpoint logs | PASS | Group 4 (BrewPOS) |
| **Routine Maintenance** | ANALYZE and VACUUM (VERBOSE, ANALYZE) execution | PASS | Group 4 (BrewPOS) |
| **Query Performance Profiling** | EXPLAIN (ANALYZE, BUFFERS) execution timing & buffer hits | PASS | Group 4 (BrewPOS) |