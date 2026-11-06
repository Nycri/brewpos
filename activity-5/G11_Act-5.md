# COMP 018: Database Administration â€” Activity 5: Database Maintenance and Monitoring
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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

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

---

> [!NOTE]
> All technical diagnostic outputs, execution plan buffers, log snippets, and vacuum records for Group 11 are archived in [Evidence-act5/G11_Act-5_Evidence.md](Evidence-act5/G11_Act-5_Evidence.md).