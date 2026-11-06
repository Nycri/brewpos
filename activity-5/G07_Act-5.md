# COMP 018: Database Administration - Activity 5: Database Maintenance and Monitoring
## Group Evaluation Report: Group 07 - CampusSafe: Campus Hazard & Safety Reporting System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **CampusSafe: Campus Hazard & Safety Reporting System** |
| **Target Project Group** | **Group 7:** Alba, Arreza, Orpesa, Robiso, Santos |
| **Target Project Repository** | [https://github.com/jeffersonorpesa2-ops/grp07-campussafe](https://github.com/jeffersonorpesa2-ops/grp07-campussafe) |
| **Primary Database** | `grp07_campussafedb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp07_campussafedb_restore_test` |

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
- **Total Database Size:** `8966 kB` (~8.9 MB)
- **Object Count:** `total_tables: 9`, `total_sequences: 9`
- **Table Storage Breakdown:**
  ```text
        table_name       | total_size | table_size | index_size 
  -----------------------+------------+------------+------------
   hazard_report         | 112 kB     | 8192 bytes | 96 kB
   assignment            | 72 kB      | 8192 bytes | 64 kB
   app_user              | 64 kB      | 8192 bytes | 48 kB
   attachment            | 48 kB      | 8192 bytes | 32 kB
   hazard_category       | 48 kB      | 8192 bytes | 32 kB
   report_status_history | 48 kB      | 8192 bytes | 32 kB
   role                  | 48 kB      | 8192 bytes | 32 kB
   maintenance_action    | 48 kB      | 8192 bytes | 32 kB
   campus_location       | 32 kB      | 8192 bytes | 16 kB
  (9 rows)
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
WHERE r.rolname IN ('postgres', 'campussafe_app', 'campussafe_report', 'campussafe_backup', 'app_runtime_grp', 'app_readonly_grp', 'app_backup_grp')
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
        datname       | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  --------------------+------------------+------------------------+--------------------------+-----------+-----------
   grp07_campussafedb |                6 |                   1210 |                        9 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. Transaction rollback ratio is < 0.74%, confirming high transaction integrity.
- **Roles Verification Output:**
  ```text
       role_name     | can_login | is_superuser |  member_of_group   
  -------------------+-----------+--------------+--------------------
   app_backup_grp    | f         | f            | {}
   app_readonly_grp  | f         | f            | {}
   app_runtime_grp   | f         | f            | {}
   campussafe_app    | t         | f            | {app_runtime_grp}
   campussafe_backup | t         | f            | {app_backup_grp}
   campussafe_report | t         | f            | {app_readonly_grp}
   postgres          | t         | t            | {}
  (7 rows)
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
      table_name       | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze |       last_autoanalyze        
-----------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+-------------------------------
 app_user              |                7 |                      24 |         182 |           6 |           6 |             |                 |              | 
 attachment            |                5 |                      16 |           1 |           5 |           1 |             |                 |              | 
 hazard_report         |               18 |                     181 |          94 |          17 |           1 |             |                 |              | 
 campus_location       |                5 |                      32 |          30 |          10 |           0 |             |                 |              | 
 maintenance_action    |                3 |                       9 |           0 |           9 |           0 |             |                 |              | 
 report_status_history |                3 |                      60 |           1 |          60 |           0 |             |                 |              | 2026-11-05 00:37:19.209883-05
 assignment            |                7 |                      36 |           2 |          12 |           0 |             |                 |              | 
 hazard_category       |                5 |                      18 |          35 |           6 |           0 |             |                 |              | 
 role                  |                3 |                       5 |           7 |           5 |           0 |             |                 |              | 
(9 rows)
```
*Analysis:* `app_user` accumulated 6 dead tuples as a result of running `seed_users.php` password hash updates. `hazard_report` and `attachment` had 1 dead tuple each from initial testing. This confirmed that routine `VACUUM` was warranted.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-04_094245.log`)
1. **Activity 4 Permission Denied Logs:**
   - **Log Entry:** `ERROR: permission denied for table hazard_category on statement INSERT INTO hazard_category (category_name) VALUES ('Rogue Category');` by `campussafe_report`
   - **Log Entry:** `ERROR: permission denied for table hazard_report on statement UPDATE hazard_report SET priority = 'Critical' WHERE report_id = 1;` by `campussafe_report`
   - **Log Entry:** `ERROR: permission denied for table hazard_report on statement DELETE FROM hazard_report WHERE report_id = 1;` by `campussafe_report`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `campussafe_app`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `campussafe_app`
   - **Assessment:** Positive validation of the Least Privilege policy. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Checkpoint Health:** Background checkpoints logged clean completions (e.g. `checkpoint complete: wrote 21 buffers (0.1%), write=2.358 s, sync=0.003 s, total=2.379 s`).
3. **Server Stability:** No crashes, unhandled segfaults, or disk corruption warnings were present in the active log.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- Standalone maintenance execution as superuser / owner:
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.hazard_report;
VACUUM (VERBOSE, ANALYZE) public.report_status_history;
VACUUM (VERBOSE, ANALYZE) public.app_user;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM hazard_report;` as `campussafe_app` produced:
  ```text
  WARNING:  permission denied to vacuum "hazard_report", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.hazard_report;` Output:**
  ```text
  INFO:  vacuuming "grp07_campussafedb.public.hazard_report"
  INFO:  finished vacuuming "grp07_campussafedb.public.hazard_report": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 1 removed, 17 remain, 0 are dead but not yet removable
  removable cutoff: 1542, which was 0 XIDs old when operation ended
  new relfrozenxid: 1447, which is 26 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 131 hits, 0 reads, 4 dirtied
  WAL usage: 6 records, 4 full page images, 33380 bytes, 0 buffers full
  INFO:  analyzing "public.hazard_report"
  INFO:  "hazard_report": scanned 1 of 1 pages, containing 17 live rows and 0 dead rows; 17 rows in sample, 17 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.app_user;` Output:**
  ```text
  INFO:  vacuuming "grp07_campussafedb.public.app_user"
  INFO:  finished vacuuming "grp07_campussafedb.public.app_user": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 6 removed, 6 remain, 0 are dead but not yet removable
  removable cutoff: 1544, which was 0 XIDs old when operation ended
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 90 hits, 0 reads, 4 dirtied
  INFO:  analyzing "public.app_user"
  INFO:  "app_user": scanned 1 of 1 pages, containing 6 live rows and 0 dead rows; 6 rows in sample, 6 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. Attempting it under `campussafe_app` resulted in a permission denial, confirming that routine maintenance must be orchestrated by a designated maintenance or administrative account (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites entire table files and acquires an `AccessExclusiveLock`, blocking concurrent SELECT, INSERT, and UPDATE queries. Because CampusSafe tables contain only dozens of rows fitting inside single 8 kB pages, standard non-blocking `VACUUM` reclaimed all dead tuples (`6 removed` in `app_user`, `1 removed` in `hazard_report`) without application downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Admin Filter by Status & Recency
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * 
FROM hazard_report 
WHERE status = 'Submitted' 
ORDER BY submitted_at DESC;
```

#### Actual Execution Plan Output
```text
                                                    QUERY PLAN                                                    
------------------------------------------------------------------------------------------------------------------
 Sort  (cost=1.24..1.24 rows=3 width=130) (actual time=0.039..0.039 rows=3.00 loops=1)
   Sort Key: submitted_at DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=4
   ->  Seq Scan on hazard_report  (cost=0.00..1.21 rows=3 width=130) (actual time=0.017..0.019 rows=3.00 loops=1)
         Filter: ((status)::text = 'Submitted'::text)
         Rows Removed by Filter: 14
         Buffers: shared hit=1
 Planning:
   Buffers: shared hit=215
 Planning Time: 5.960 ms
 Execution Time: 0.062 ms
(12 rows)
```

#### Interpretation
1. **Execution Time:** `0.062 ms` (ultra-fast sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 4`, with 0 disk block reads (100% in-memory cache hit).
3. **Sequential Scan Justification:** Even though an index `idx_report_status` exists on `status`, the PostgreSQL query planner deliberately chose a `Seq Scan` on `hazard_report`. Because the entire table fits inside a single 8 kB heap block (`Buffers: shared hit = 1`), sequentially scanning that block in memory is computationally cheaper than loading the B-Tree index page followed by random pointer lookups into the heap.

---

### Query 2: Hazard Reports with Joined Categories and Locations
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.report_id, c.category_name, l.building, l.room_area, r.status, r.priority
FROM hazard_report r
JOIN hazard_category c ON c.category_id = r.category_id
JOIN campus_location l ON l.location_id = r.location_id
WHERE r.status = 'In Progress';
```

#### Actual Execution Plan Output
```text
                                                           QUERY PLAN                                                            
---------------------------------------------------------------------------------------------------------------------------------
 Hash Join  (cost=2.36..3.59 rows=3 width=69) (actual time=0.042..0.044 rows=3.00 loops=1)
   Hash Cond: (r.location_id = l.location_id)
   Buffers: shared hit=3
   ->  Hash Join  (cost=1.14..2.36 rows=3 width=34) (actual time=0.020..0.022 rows=3.00 loops=1)
         Hash Cond: (r.category_id = c.category_id)
         Buffers: shared hit=2
         ->  Seq Scan on hazard_report r  (cost=0.00..1.21 rows=3 width=27) (actual time=0.005..0.006 rows=3.00 loops=1)
               Filter: ((status)::text = 'In Progress'::text)
               Rows Removed by Filter: 14
               Buffers: shared hit=1
         ->  Hash  (cost=1.06..1.06 rows=6 width=15) (actual time=0.008..0.008 rows=6.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on hazard_category c  (cost=0.00..1.06 rows=6 width=15) (actual time=0.004..0.004 rows=6.00 loops=1)
                     Buffers: shared hit=1
   ->  Hash  (cost=1.10..1.10 rows=10 width=43) (actual time=0.018..0.018 rows=10.00 loops=1)
         Buckets: 1024  Batches: 1  Memory Usage: 9kB
         Buffers: shared hit=1
         ->  Seq Scan on campus_location l  (cost=0.00..1.10 rows=10 width=43) (actual time=0.008..0.009 rows=10.00 loops=1)
               Buffers: shared hit=1
 Planning:
   Buffers: shared hit=168
 Planning Time: 2.107 ms
 Execution Time: 0.069 ms
(24 rows)
```

#### Interpretation
1. **Execution Time:** `0.069 ms`.
2. **Buffer Hits:** `shared hit = 3` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner built in-memory hash tables for `hazard_category` (9 kB) and `campus_location` (9 kB), executing two chained `Hash Joins` with zero disk spills (`Batches: 1`).

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current schema indexes | All 24 existing indexes (`idx_report_status`, `idx_report_category`, `idx_report_location`, etc.) are properly defined. Query execution times are well under 0.1 ms with 100% cache hits. Additional indexing would introduce insert/update overhead without measurable latency gains. |
| **Follow-up Action** | Composite Index for Large-Scale Incident Volumes | If the system scales past 10,000 incident reports in production, queries filtering by status and sorting by recency will benefit from a composite index: `CREATE INDEX idx_hazard_report_status_submitted ON hazard_report (status, submitted_at DESC);`. |
| **Follow-up Action** | Automated Maintenance Script via Task Scheduler | Deploy a scheduled weekly maintenance job invoking `VACUUM (ANALYZE)` during low-traffic windows (e.g. Sunday 02:00 AM) to prevent dead tuple buildup as maintenance technicians update work orders. |

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 7 - CampusSafe: Campus Hazard & Safety Reporting System |
| **Database inspected** | `grp07_campussafedb` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `campussafe_app` (Runtime), `campussafe_report` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 8.9 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on hazard_report, report_status_history, app_user via postgres` |
| **Query analyzed** | Admin status filter with sort (Seq scan, 0.062 ms, 4 buffer hits) & Multi-table join (Hash joins, 0.069 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.07 ms with 100% buffer cache hits) |
| **Evidence folder link** | `https://github.com/Nycri/brewpos/tree/main/activity-5/Evidence-act5` |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

---

> [!NOTE]
> All technical diagnostic outputs, execution plan buffers, log snippets, and vacuum records for Group 07 are archived in [Evidence-act5/G07_Act-5_Evidence.md](Evidence-act5/G07_Act-5_Evidence.md).