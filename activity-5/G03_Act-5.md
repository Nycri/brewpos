# COMP 018: Database Administration â€” Activity 5: Database Maintenance and Monitoring
## Group Evaluation Report: Group 03 â€” OJT Management System

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **OJT Management System** (Project-OJT-RMA-System) |
| **Target Project Group** | **Group 3 (Evaluated under Tracker Key: Group 06 / Group 03):** Simon Russell Shaun; De Dios, Niño; Ramos, Jared; Dela Rosa, Rovi; Sietereales, Andrei; Geraldez, Mark Angelo |
| **Target Project Repository** | [https://github.com/swvenz/Project-OJT-RMA-System](https://github.com/swvenz/Project-OJT-RMA-System) (Branches: `main`, `ojt-system-untested.demo`) |
| **Primary Database** | `ojt_management` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `ojt_management_restore_test` |

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

-- 4. Total Number of Tables and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public') AS total_sequences;

-- 5. Table Inventory and Individual Table Sizes
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident(table_name))) AS index_size
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Total Database Size:** `9046 kB` (~9.0 MB)
- **Object Count:** `total_tables: 12`, `total_sequences: 12`
- **Table Storage Breakdown:**
  ```text
        table_name      | total_size | table_size | index_size 
  ----------------------+------------+------------+------------
   users                | 64 kB      | 8192 bytes | 48 kB
   students             | 56 kB      | 8192 bytes | 48 kB
   ojt_applications     | 48 kB      | 8192 bytes | 32 kB
   requirements         | 48 kB      | 8192 bytes | 32 kB
   student_requirements | 48 kB      | 8192 bytes | 32 kB
   companies            | 48 kB      | 8192 bytes | 32 kB
   ojt_placements       | 40 kB      | 8192 bytes | 32 kB
   attendance           | 40 kB      | 8192 bytes | 32 kB
   company_supervisors  | 40 kB      | 8192 bytes | 32 kB
   academic_supervisors | 40 kB      | 8192 bytes | 32 kB
   evaluations          | 32 kB      | 8192 bytes | 16 kB
   audit_log            | 32 kB      | 8192 bytes | 16 kB
  (12 rows)
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
WHERE r.rolname LIKE 'ojt%'
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
      datname     | numbackends | xact_commit | xact_rollback | conflicts | deadlocks 
  ----------------+-------------+-------------+---------------+-----------+-----------
   ojt_management |           1 |         116 |            10 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The rolled-back transactions (10) directly reflect intentional negative security tests during Activity 4 verification (e.g., unauthorized DDL drops, illegal table deletions, and cross-database connection attempts).
- **Roles Verification Output:**
  ```text
      role_name     | can_login | is_superuser |  member_of_group   
  ------------------+-----------+--------------+--------------------
   ojt_app          | t         | f            | {ojt_app_grp}
   ojt_app_grp      | f         | f            | {}
   ojt_backup       | t         | f            | {ojt_backup_grp}
   ojt_backup_grp   | f         | f            | {}
   ojt_readonly_grp | f         | f            | {}
   ojt_report_user  | t         | f            | {ojt_readonly_grp}
   ojt_student_grp  | f         | f            | {}
   ojt_student_user | t         | f            | {ojt_student_grp}
  (8 rows)
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
ORDER BY n_dead_tup DESC, relname ASC;
```

### Actual Output
```text
      table_name      | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
----------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 ojt_applications     |                2 |                       0 |           8 |           2 |           2 |             |                 |              | 
 academic_supervisors |                2 |                       0 |           6 |           2 |           0 |             |                 |              | 
 attendance           |                2 |                       0 |           4 |           4 |           0 |             |                 |              | 
 audit_log            |                1 |                       0 |           0 |           2 |           0 |             |                 |              | 
 companies            |                4 |                       6 |           7 |           3 |           0 |             |                 |              | 
 company_supervisors  |                2 |                       0 |           3 |           1 |           0 |             |                 |              | 
 evaluations          |                2 |                       1 |           1 |           1 |           0 |             |                 |              | 
 ojt_placements       |                3 |                       1 |           7 |           1 |           0 |             |                 |              | 
 requirements         |                2 |                       0 |           9 |           5 |           0 |             |                 |              | 
 student_requirements |                2 |                       0 |           4 |           4 |           0 |             |                 |              | 
 students             |                3 |                       0 |          10 |           2 |           0 |             |                 |              | 
 users                |                3 |                       0 |          12 |           7 |           0 |             |                 |              | 
(12 rows)
```
*Analysis:* `ojt_applications` accumulated 2 dead tuples as a direct result of Activity 4's insert, update, and deletion test for `application_id = 3`. All other operational tables exhibited 0 dead tuples. This demonstrated that routine `VACUUM` was warranted to reclaim dead tuple pointers.

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Least-Privilege Denials Logged:**
   - **Log Entry:** `ERROR: permission denied for table students on statement DELETE FROM students WHERE student_id = 1;` by `ojt_app`
   - **Log Entry:** `ERROR: permission denied for table attendance on statement DELETE FROM attendance WHERE attendance_id = 1;` by `ojt_app`
   - **Log Entry:** `ERROR: permission denied for table evaluations on statement DELETE FROM evaluations WHERE evaluation_id = 1;` by `ojt_app`
   - **Log Entry:** `ERROR: permission denied for table users on statement SELECT * FROM users;` by `ojt_report_user`
   - **Log Entry:** `ERROR: permission denied for table requirements on statement INSERT INTO requirements ...;` by `ojt_report_user`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `ojt_app`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `ojt_app`
   - **Assessment:** Positive validation of least privilege. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Maintenance Boundary Warning:**
   - **Log Entry:** `WARNING: permission denied to vacuum "ojt_applications", skipping it` by `ojt_app`
   - **Assessment:** Positive validation of maintenance boundaries. Runtime accounts cannot initiate storage maintenance tasks.
3. **Server Stability:** No unexpected crashes, unhandled segfaults, memory leaks, or disk corruption warnings were present in the active log.

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- 1. Test Permission Boundary (as runtime application user ojt_app):
VACUUM ojt_applications;

-- 2. Standalone Maintenance Execution (as superuser / owner postgres):
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.ojt_applications;
VACUUM (VERBOSE, ANALYZE) public.attendance;
VACUUM (VERBOSE, ANALYZE) public.evaluations;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM ojt_applications;` as `ojt_app` produced:
  ```text
  WARNING:  permission denied to vacuum "ojt_applications", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.ojt_applications;` Output:**
  ```text
  INFO:  vacuuming "ojt_management.public.ojt_applications"
  INFO:  finished vacuuming "ojt_management.public.ojt_applications": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 2 removed, 2 remain, 0 are dead but not yet removable
  removable cutoff: 2592, which was 0 XIDs old when operation ended
  new relfrozenxid: 2299, which is 26 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 83 hits, 0 reads, 6 dirtied
  WAL usage: 9 records, 6 full page images, 33736 bytes, 0 buffers full
  INFO:  analyzing "public.ojt_applications"
  INFO:  "ojt_applications": scanned 1 of 1 pages, containing 2 live rows and 0 dead rows; 2 rows in sample, 2 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.attendance;` Output:**
  ```text
  INFO:  vacuuming "ojt_management.public.attendance"
  INFO:  finished vacuuming "ojt_management.public.attendance": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 4 remain, 0 are dead but not yet removable
  buffer usage: 86 hits, 0 reads, 5 dirtied
  WAL usage: 5 records, 6 full page images, 40751 bytes, 0 buffers full
  INFO:  analyzing "public.attendance"
  INFO:  "attendance": scanned 1 of 1 pages, containing 4 live rows and 0 dead rows; 4 rows in sample, 4 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.evaluations;` Output:**
  ```text
  INFO:  vacuuming "ojt_management.public.evaluations"
  INFO:  finished vacuuming "ojt_management.public.evaluations": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 0 removed, 1 remain, 0 are dead but not yet removable
  buffer usage: 58 hits, 0 reads, 4 dirtied
  WAL usage: 5 records, 5 full page images, 33515 bytes, 0 buffers full
  INFO:  analyzing "public.evaluations"
  INFO:  "evaluations": scanned 1 of 1 pages, containing 1 live rows and 0 dead rows; 1 rows in sample, 1 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. Attempting it under `ojt_app` resulted in a permission denial (`WARNING: permission denied to vacuum "ojt_applications", skipping it`), confirming that routine maintenance must be orchestrated by a designated maintenance or administrative account (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites entire table files and acquires an `AccessExclusiveLock`, completely locking tables against concurrent `SELECT`, `INSERT`, and `UPDATE` statements—which would freeze student attendance logging and application workflows. Standard non-blocking `VACUUM` cleanly reclaimed all 2 dead tuples in `ojt_applications` without application downtime.

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Student Placement and Verified Attendance Progress Lookup
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    p.placement_id, 
    s.first_name || ' ' || s.last_name AS student_name, 
    c.company_name, 
    p.status, 
    (SELECT COALESCE(SUM(t.hours_rendered), 0) 
     FROM attendance t 
     WHERE t.placement_id = p.placement_id AND t.is_verified) AS verified_hours, 
    p.required_hours, 
    p.start_date, 
    p.end_date 
FROM ojt_placements p 
JOIN students s ON s.student_id = p.student_id 
JOIN companies c ON c.company_id = p.company_id 
WHERE p.deleted_at IS NULL 
ORDER BY p.placement_id DESC;
```

#### Actual Execution Plan Output
```text
                                                           QUERY PLAN                                                           
--------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=4.21..4.21 rows=1 width=106) (actual time=0.078..0.079 rows=1.00 loops=1)
   Sort Key: p.placement_id DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=7
   ->  Nested Loop  (cost=0.00..4.20 rows=1 width=106) (actual time=0.062..0.063 rows=1.00 loops=1)
         Join Filter: (c.company_id = p.company_id)
         Buffers: shared hit=4
         ->  Nested Loop  (cost=0.00..2.06 rows=1 width=40) (actual time=0.024..0.025 rows=1.00 loops=1)
               Join Filter: (s.student_id = p.student_id)
               Buffers: shared hit=2
               ->  Seq Scan on ojt_placements p  (cost=0.00..1.01 rows=1 width=32) (actual time=0.015..0.015 rows=1.00 loops=1)
                     Filter: (deleted_at IS NULL)
                     Buffers: shared hit=1
               ->  Seq Scan on students s  (cost=0.00..1.02 rows=2 width=16) (actual time=0.007..0.007 rows=1.00 loops=1)
                     Buffers: shared hit=1
         ->  Seq Scan on companies c  (cost=0.00..1.03 rows=3 width=22) (actual time=0.007..0.007 rows=1.00 loops=1)
               Buffers: shared hit=1
         SubPlan 1
           ->  Aggregate  (cost=1.06..1.07 rows=1 width=32) (actual time=0.025..0.025 rows=1.00 loops=1)
                 Buffers: shared hit=1
                 ->  Seq Scan on attendance t  (cost=0.00..1.05 rows=3 width=5) (actual time=0.009..0.009 rows=3.00 loops=1)
                       Filter: (is_verified AND (placement_id = p.placement_id))
                       Rows Removed by Filter: 1
                       Buffers: shared hit=1
 Planning:
   Buffers: shared hit=275
 Planning Time: 7.246 ms
 Execution Time: 0.140 ms
(28 rows)
```

#### Interpretation
1. **Execution Time:** `0.140 ms` (sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 7`, with 0 physical disk block reads (100% in-memory buffer cache hit).
3. **Execution Strategy:** The query planner executed a `Nested Loop` across `ojt_placements`, `students`, and `companies`, and evaluated `SubPlan 1` for aggregate verified attendance hours directly in memory (`quicksort Memory: 25kB`).

---

### Query 2: Multi-Table Academic Requirement Compliance Review
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    sr.student_req_id, 
    s.first_name || ' ' || s.last_name AS student_name, 
    s.student_number,
    r.title AS requirement_title, 
    sr.file_path, 
    sr.status AS review_status,
    sr.submission_date 
FROM student_requirements sr 
JOIN students s ON s.student_id = sr.student_id 
JOIN requirements r ON r.req_id = sr.req_id 
WHERE sr.submission_date IS NOT NULL 
ORDER BY sr.student_req_id ASC;
```

#### Actual Execution Plan Output
```text
                                                              QUERY PLAN                                                               
---------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=3.31..3.32 rows=4 width=124) (actual time=0.082..0.084 rows=4.00 loops=1)
   Sort Key: sr.student_req_id
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=6
   ->  Hash Join  (cost=2.16..3.27 rows=4 width=124) (actual time=0.058..0.061 rows=4.00 loops=1)
         Hash Cond: (sr.req_id = r.req_id)
         Buffers: shared hit=3
         ->  Hash Join  (cost=1.04..2.12 rows=4 width=92) (actual time=0.040..0.042 rows=4.00 loops=1)
               Hash Cond: (sr.student_id = s.student_id)
               Buffers: shared hit=2
               ->  Seq Scan on student_requirements sr  (cost=0.00..1.04 rows=4 width=74) (actual time=0.016..0.017 rows=4.00 loops=1)
                     Filter: (submission_date IS NOT NULL)
                     Buffers: shared hit=1
               ->  Hash  (cost=1.02..1.02 rows=2 width=26) (actual time=0.017..0.017 rows=2.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on students s  (cost=0.00..1.02 rows=2 width=26) (actual time=0.007..0.007 rows=2.00 loops=1)
                           Buffers: shared hit=1
         ->  Hash  (cost=1.05..1.05 rows=5 width=20) (actual time=0.008..0.008 rows=5.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on requirements r  (cost=0.00..1.05 rows=5 width=20) (actual time=0.003..0.004 rows=5.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=232
 Planning Time: 5.068 ms
 Execution Time: 0.126 ms
(27 rows)
```

#### Interpretation
1. **Execution Time:** `0.126 ms`.
2. **Buffer Hits:** `shared hit = 6` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner built in-memory hash tables for `students` (9 kB) and `requirements` (9 kB), executing two chained `Hash Joins` with zero disk spills (`Batches: 1`).

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain existing primary, foreign key, and unique indexes | All 12 tables fit inside 8 kB heap pages, and current multi-table queries execute in < 0.15 ms with 100% cache hits. Adding indexes now would introduce write overhead without improving query latency. |
| **Follow-up Action** | Composite Index for Large-Scale Attendance Verification | When student internship attendance logs scale past 25,000 entries, supervisor verification queries will benefit from a composite partial index: `CREATE INDEX idx_attendance_unverified ON attendance (placement_id, attendance_date) WHERE is_verified = FALSE;`. |
| **Follow-up Action** | Automated Weekly Maintenance via Task Scheduler | Deploy a scheduled maintenance script invoking `VACUUM (ANALYZE)` nightly at 02:00 AM to automatically clean up dead tuples as student applications and requirement reviews cycle through update stages. |

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 3 - OJT Management System (Project-OJT-RMA-System) [Tracker Key: Group 06 / Group 03] |
| **Database inspected** | `ojt_management` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `ojt_app` (Runtime), `ojt_report_user` (Audit) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 9.0 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on ojt_applications, attendance, evaluations via postgres` |
| **Query analyzed** | Student placement progress & verified hours (Nested Loop, 0.140 ms, 7 buffer hits) & Requirements compliance review (Hash Joins, 0.126 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.15 ms with 100% buffer cache hits) |
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---

> [!NOTE]
> All technical diagnostic outputs, execution plan buffers, log snippets, and vacuum records for Group 03 are archived in [Evidence-act5/G03_Act-5_Evidence.md](Evidence-act5/G03_Act-5_Evidence.md).