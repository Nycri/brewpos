# Activity 5 Technical Evidence Dossier â€” Group 02: Park-It: Parking Management System
## COMP 018: Database Maintenance & Monitoring Technical Evidence Audit

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

# EVIDENCE DOSSIER & VERIFICATION ARTIFACTS

This evidence dossier contains the unedited diagnostic queries, terminal execution outputs, log entries, and performance plans executed during Activity 5 for **Group 02 (Park-It: Parking Management System)**.

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
- **Total Database Size:** `8438 kB` (~8.4 MB)
- **Object Count:** `total_tables: 5`, `total_sequences: 5`
- **Table Storage Breakdown:**
  ```text
    table_name  | total_size | table_size | index_size 
  --------------+------------+------------+------------
   parking_zone | 40 kB      | 8192 bytes | 32 kB
   staff_user   | 40 kB      | 8192 bytes | 32 kB
   vehicle      | 40 kB      | 8192 bytes | 32 kB
   payment      | 40 kB      | 8192 bytes | 32 kB
   transaction  | 24 kB      | 8192 bytes | 16 kB
  (5 rows)
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
WHERE r.rolname LIKE 'parkit%'
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
     datname    | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  --------------+------------------+------------------------+--------------------------+-----------+-----------
   grp_parkitdb |                4 |                   1205 |                       13 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The transaction rollback ratio is < 1.07%, demonstrating exceptional transaction integrity.
- **Roles Verification Output:**
  ```text
        role_name      | can_login | is_superuser |    member_of_group    
  ---------------------+-----------+--------------+-----------------------
   parkit_app          | t         | f            | {parkit_app_grp}
   parkit_app_grp      | f         | f            | {}
   parkit_backup       | t         | f            | {parkit_backup_grp}
   parkit_backup_grp   | f         | f            | {}
   parkit_readonly_grp | f         | f            | {}
   parkit_report_user  | t         | f            | {parkit_readonly_grp}
  (6 rows)
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

### Actual Output
```text
  table_name  | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
--------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 parking_zone |                6 |                      12 |          17 |           5 |           2 |             |                 |              | 
 transaction  |                7 |                      15 |           8 |           5 |           0 |             |                 |              | 
 vehicle      |                4 |                       5 |          15 |           5 |           0 |             |                 |              | 
 staff_user   |                4 |                       4 |           4 |           4 |           0 |             |                 |              | 
 payment      |                5 |                       3 |           3 |           3 |           0 |             |                 |              | 
(5 rows)
```
*Analysis:* `parking_zone` accumulated 2 dead tuples as a direct result of Activity 4's insert/update/delete permission test for `Activity 4 Test Zone`. All other operational tables showed 0 dead tuples. This confirmed that routine `VACUUM` was warranted to reclaim unused slot pointers.

---

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Permission Denied Logs:**
   - **Log Entry:** `ERROR: permission denied for table transaction on statement DELETE FROM transaction WHERE transaction_id = 1;` by `parkit_app`
   - **Log Entry:** `ERROR: permission denied for table staff_user on statement DELETE FROM staff_user WHERE user_id = 1;` by `parkit_app`
   - **Log Entry:** `ERROR: permission denied for table staff_user on statement SELECT * FROM staff_user;` by `parkit_report_user`
   - **Log Entry:** `ERROR: permission denied for table parking_zone on statement INSERT INTO parking_zone (zone_name, max_capacity, current_active_count) VALUES ('Unauthorized Zone', 10, 0);` by `parkit_report_user`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `parkit_app`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `parkit_app`
   - **Assessment:** Positive validation of least privilege. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Checkpoint Health:** Background checkpoints logged clean completions (e.g., `checkpoint complete: wrote 12 buffers (0.1%), write=1.320 s, sync=0.005 s, total=1.340 s`).
3. **Server Stability:** No unhandled crashes, memory leaks, or disk corruption warnings were observed.

---

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- Standalone maintenance execution as superuser / owner:
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.parking_zone;
VACUUM (VERBOSE, ANALYZE) public.transaction;
VACUUM (VERBOSE, ANALYZE) public.payment;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM parking_zone;` as `parkit_app` produced:
  ```text
  WARNING:  permission denied to vacuum "parking_zone", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.parking_zone;` Output:**
  ```text
  INFO:  vacuuming "grp_parkitdb.public.parking_zone"
  INFO:  finished vacuuming "grp_parkitdb.public.parking_zone": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 2 removed, 5 remain, 0 are dead but not yet removable
  removable cutoff: 1759, which was 0 XIDs old when operation ended
  new relfrozenxid: 1226, which is 42 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 85 hits, 0 reads, 6 dirtied
  WAL usage: 9 records, 6 full page images, 33888 bytes, 0 buffers full
  INFO:  analyzing "public.parking_zone"
  INFO:  "parking_zone": scanned 1 of 1 pages, containing 5 live rows and 0 dead rows; 5 rows in sample, 5 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.transaction;` Output:**
  ```text
  INFO:  vacuuming "grp_parkitdb.public.transaction"
  INFO:  finished vacuuming "grp_parkitdb.public.transaction": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 5 remain, 0 are dead but not yet removable
  buffer usage: 64 hits, 0 reads, 4 dirtied
  INFO:  analyzing "public.transaction"
  INFO:  "transaction": scanned 1 of 1 pages, containing 5 live rows and 0 dead rows; 5 rows in sample, 5 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.payment;` Output:**
  ```text
  INFO:  vacuuming "grp_parkitdb.public.payment"
  INFO:  finished vacuuming "grp_parkitdb.public.payment": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 3 remain, 0 are dead but not yet removable
  buffer usage: 70 hits, 0 reads, 5 dirtied
  INFO:  analyzing "public.payment"
  INFO:  "payment": scanned 1 of 1 pages, containing 3 live rows and 0 dead rows; 3 rows in sample, 3 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. When attempted under `parkit_app`, PostgreSQL emitted `WARNING: permission denied to vacuum "parking_zone", skipping it`. This confirms that runtime application accounts cannot trigger maintenance operations.
- **Exclusion of `VACUUM FULL`:** In accordance with DBA best practices, `VACUUM FULL` was deliberately avoided. It acquires an `AccessExclusiveLock`, completely blocking concurrent `SELECT`, `INSERT`, and `UPDATE` statements — which would freeze live vehicle entries and exits. Standard non-blocking `VACUUM` reclaimed all 2 dead tuples in `parking_zone` without downtime.

---

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Active Parking Zone Capacity & Availability
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT zone_id, zone_name, max_capacity, current_active_count 
FROM parking_zone 
WHERE current_active_count < max_capacity 
ORDER BY current_active_count ASC;
```

#### Actual Execution Plan Output
```text
                                                   QUERY PLAN                                                   
----------------------------------------------------------------------------------------------------------------
 Sort  (cost=1.07..1.08 rows=2 width=29) (actual time=0.025..0.025 rows=5.00 loops=1)
   Sort Key: current_active_count
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=4
   ->  Seq Scan on parking_zone  (cost=0.00..1.06 rows=2 width=29) (actual time=0.013..0.013 rows=5.00 loops=1)
         Filter: (current_active_count < max_capacity)
         Buffers: shared hit=1
 Planning:
   Buffers: shared hit=103
 Planning Time: 1.761 ms
 Execution Time: 0.050 ms
(11 rows)
```

#### Interpretation
1. **Execution Time:** `0.050 ms` (sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 4`, with 0 physical disk block reads (100% in-memory cache hit ratio).
3. **Sequential Scan Justification:** The query planner chose a sequential scan over an index scan. Because the entire `parking_zone` table fits within a single 8 kB heap block (`Buffers: shared hit = 1`), reading that block sequentially in memory is significantly cheaper than loading index B-Tree pages followed by pointer lookups.

---

### Query 2: Multi-Table Vehicle Stay & Payment Billing History
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    t.transaction_id,
    v.license_plate,
    v.vehicle_type,
    z.zone_name,
    t.entry_time,
    t.exit_time,
    p.amount,
    p.payment_method
FROM transaction t
JOIN vehicle v ON t.vehicle_id = v.vehicle_id
JOIN parking_zone z ON t.zone_id = z.zone_id
LEFT JOIN payment p ON t.transaction_id = p.transaction_id
WHERE t.status = 'Completed'
ORDER BY t.entry_time DESC;
```

#### Actual Execution Plan Output
```text
                                                             QUERY PLAN                                                              
-------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=4.44..4.44 rows=3 width=64) (actual time=0.283..0.286 rows=3.00 loops=1)
   Sort Key: t.entry_time DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=7
   ->  Hash Join  (cost=3.29..4.41 rows=3 width=64) (actual time=0.251..0.258 rows=3.00 loops=1)
         Hash Cond: (t.zone_id = z.zone_id)
         Buffers: shared hit=4
         ->  Hash Join  (cost=2.18..3.28 rows=3 width=51) (actual time=0.136..0.141 rows=3.00 loops=1)
               Hash Cond: (t.vehicle_id = v.vehicle_id)
               Buffers: shared hit=3
               ->  Hash Left Join  (cost=1.07..2.15 rows=3 width=40) (actual time=0.115..0.119 rows=3.00 loops=1)
                     Hash Cond: (t.transaction_id = p.transaction_id)
                     Buffers: shared hit=2
                     ->  Seq Scan on transaction t  (cost=0.00..1.06 rows=3 width=28) (actual time=0.072..0.074 rows=3.00 loops=1)
                           Filter: ((status)::text = 'Completed'::text)
                           Rows Removed by Filter: 2
                           Buffers: shared hit=1
                     ->  Hash  (cost=1.03..1.03 rows=3 width=16) (actual time=0.026..0.026 rows=3.00 loops=1)
                           Buckets: 1024  Batches: 1  Memory Usage: 9kB
                           Buffers: shared hit=1
                           ->  Seq Scan on payment p  (cost=0.00..1.03 rows=3 width=16) (actual time=0.021..0.022 rows=3.00 loops=1)
                                 Buffers: shared hit=1
               ->  Hash  (cost=1.05..1.05 rows=5 width=19) (actual time=0.015..0.015 rows=5.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on vehicle v  (cost=0.00..1.05 rows=5 width=19) (actual time=0.005..0.006 rows=5.00 loops=1)
                           Buffers: shared hit=1
         ->  Hash  (cost=1.05..1.05 rows=5 width=21) (actual time=0.042..0.044 rows=5.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on parking_zone z  (cost=0.00..1.05 rows=5 width=21) (actual time=0.024..0.025 rows=5.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=341
 Planning Time: 5.849 ms
 Execution Time: 0.378 ms
(36 rows)
```

#### Interpretation
1. **Execution Time:** `0.378 ms`.
2. **Buffer Hits:** `shared hit = 7` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner executed a `Hash Left Join` between `transaction` and `payment`, followed by chained `Hash Joins` with `vehicle` and `parking_zone`. In-memory hash tables required only 9 kB each, executing with `Batches: 1` (zero disk spills).

---

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain existing primary & unique indexes | All tables are small (fitting within 8 kB pages), and current queries execute in < 0.38 ms with 100% buffer cache hits. Adding indexes now would introduce write overhead without improving latency. |
| **Follow-up Action** | Composite Index for Large-Scale Vehicle Tracking | If the parking facility scales past 20,000 transactions, queries filtering by zone and sorting by entry recency will benefit from a composite index: `CREATE INDEX idx_transaction_zone_entry ON transaction (zone_id, entry_time DESC);`. |
| **Follow-up Action** | Automated Weekly Maintenance via Task Scheduler | Deploy a scheduled maintenance script invoking `VACUUM (ANALYZE)` nightly at 02:00 AM to automatically clean up dead tuples as parking spaces frequently update occupancy counts. |

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