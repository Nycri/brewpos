# Activity 5 Technical Evidence Dossier - Group 05: PUP-SPOT: Campus Lost and Found System
## COMP 018: Database Maintenance & Monitoring Technical Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **PUP-SPOT: Campus Lost and Found System** |
| **Target Project Group** | **Group 5:** Raguin, Justine Philip; Cueto, Daniel Luigi; Imperial, Aaron Paul; Manansala, Emmanuel John; Napeñas, Jenver |
| **Target Project Repository** | [https://github.com/grp05-dba/grp05_lostandfound](https://github.com/grp05-dba/grp05_lostandfound) |
| **Primary Database** | `grp05_lostandfounddb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp05_lostandfounddb_restore_test` |

---

# EVIDENCE DOSSIER & VERIFICATION ARTIFACTS

This evidence dossier contains the unedited diagnostic queries, terminal execution outputs, log entries, and performance plans executed during Activity 5 for **Group 05 (PUP-SPOT: Campus Lost and Found System)**.

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
- **Total Database Size:** `8590 kB` (~8.6 MB)
- **Object Count:** `total_tables: 8`, `total_sequences: 8`
- **Table Storage Breakdown:**
  ```text
       table_name     | total_size | table_size | index_size 
  --------------------+------------+------------+------------
   lost_reports       | 48 kB      | 8192 bytes | 32 kB
   claims             | 48 kB      | 8192 bytes | 32 kB
   items              | 32 kB      | 8192 bytes | 16 kB
   potential_matches  | 24 kB      | 8192 bytes | 16 kB
   item_images        | 24 kB      | 8192 bytes | 16 kB
   categories         | 24 kB      | 8192 bytes | 16 kB
   claim_images       | 8192 bytes | 0 bytes    | 8192 bytes
   lost_report_images | 8192 bytes | 0 bytes    | 8192 bytes
  (8 rows)
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
WHERE r.rolname IN ('postgres', 'lostandfound_app', 'lostandfound_public', 'lostandfound_backup', 'lostandfound_app_grp', 'lostandfound_public_grp', 'lostandfound_backup_grp')
ORDER BY r.rolname;
```

### Actual Output
- **Database Statistics:**
  ```text
         datname        | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  ----------------------+------------------+------------------------+--------------------------+-----------+-----------
   grp05_lostandfounddb |                1 |                     63 |                        7 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The rolled-back transaction count directly reflects expected error conditions intentionally triggered during negative security verification testing (e.g., unauthorized DDL and cross-database access attempts).
- **Roles Verification Output:**
  ```text
          role_name        | can_login | is_superuser |      member_of_group      
  -------------------------+-----------+--------------+---------------------------
   lostandfound_app        | t         | f            | {lostandfound_app_grp}
   lostandfound_app_grp    | f         | f            | {}
   lostandfound_backup     | t         | f            | {lostandfound_backup_grp}
   lostandfound_backup_grp | f         | f            | {}
   lostandfound_public     | t         | f            | {lostandfound_public_grp}
   lostandfound_public_grp | f         | f            | {}
   postgres                | t         | t            | {}
  (7 rows)
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
     table_name     | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
--------------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 items              |                7 |                      28 |          11 |           7 |           1 |             |                 |              | 
 item_images        |                3 |                       1 |           0 |           0 |           1 |             |                 |              | 
 lost_report_images |                2 |                       0 |           0 |           0 |           0 |             |                 |              | 
 claims             |                4 |                       3 |           0 |           3 |           0 |             |                 |              | 
 potential_matches  |                2 |                       6 |           0 |           6 |           0 |             |                 |              | 
 categories         |                5 |                      44 |          20 |          11 |           0 |             |                 |              | 
 lost_reports       |                5 |                      14 |           6 |           8 |           0 |             |                 |              | 
 claim_images       |                2 |                       0 |           0 |           0 |           0 |             |                 |              | 
(8 rows)
```
*Analysis:* Prior to routine maintenance, `items` accumulated 1 dead tuple as a result of the status update in Activity 4 Test 1.3. `item_images` also accumulated 1 dead tuple from the temporary attachment insert and delete in Test 1.4. This confirmed that routine `VACUUM` was warranted.

---

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Permission-Denied Security Auditing:**
   - **Log Entry:** `ERROR: permission denied for table items on statement UPDATE items SET status = 'claimed' WHERE item_id = 1;` by `lostandfound_public`
   - **Log Entry:** `ERROR: permission denied for table items on statement DELETE FROM items WHERE item_id = 1;` by `lostandfound_public`
   - **Log Entry:** `ERROR: permission denied for table categories on statement INSERT INTO categories (name) VALUES ('Unauthorized Category');` by `lostandfound_public`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_test_db;` by `lostandfound_app`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `lostandfound_app`
   - **Assessment:** Positive validation of the Least Privilege policy. Every unauthorized operation attempted during Activity 4 testing was properly intercepted and logged by PostgreSQL.
2. **Server Stability:** No unexpected crashes, unhandled segfaults, memory leaks, or disk corruption warnings were present in the active log.
3. **Authentication Handshakes:** All database connections authenticated cleanly using modern `scram-sha-256` password encryption.

---

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Commands Executed
```sql
-- 1. Test Permission Boundary (as runtime application user lostandfound_app):
VACUUM items;

-- 2. Standalone Maintenance Execution (as superuser / owner postgres):
ANALYZE;
VACUUM (VERBOSE, ANALYZE) public.items;
VACUUM (VERBOSE, ANALYZE) public.lost_reports;
VACUUM (VERBOSE, ANALYZE) public.item_images;
```

### Actual Output
- **Permission Boundary Test:** Running `VACUUM items;` as `lostandfound_app` produced:
  ```text
  WARNING:  permission denied to vacuum "items", skipping it
  VACUUM
  ```
  This proves that runtime application accounts cannot perform maintenance operations.
- **`ANALYZE;` Output:**
  ```text
  ANALYZE
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.items;` Output:**
  ```text
  INFO:  vacuuming "grp05_lostandfounddb.public.items"
  INFO:  finished vacuuming "grp05_lostandfounddb.public.items": index scans: 0
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 1 removed, 7 remain, 0 are dead but not yet removable
  removable cutoff: 1898, which was 0 XIDs old when operation ended
  new relfrozenxid: 1839, which is 8 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  buffer usage: 58 hits, 0 reads, 4 dirtied
  WAL usage: 6 records, 4 full page images, 33380 bytes, 0 buffers full
  INFO:  analyzing "public.items"
  INFO:  "items": scanned 1 of 1 pages, containing 7 live rows and 0 dead rows; 7 rows in sample, 7 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) public.item_images;` Output:**
  ```text
  INFO:  vacuuming "grp05_lostandfounddb.public.item_images"
  INFO:  table "item_images": truncated 1 to 0 pages
  INFO:  finished vacuuming "grp05_lostandfounddb.public.item_images": index scans: 1
  pages: 1 removed, 0 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 1 removed, 0 remain, 0 are dead but not yet removable
  removable cutoff: 1900, which was 1 XIDs old when operation ended
  new relfrozenxid: 1900, which is 65 XIDs ahead of previous value
  index scan needed: 1 pages from table (100.00% of total) had 1 dead item identifiers removed
  buffer usage: 66 hits, 0 reads, 5 dirtied
  WAL usage: 10 records, 5 full page images, 33609 bytes, 0 buffers full
  INFO:  analyzing "public.item_images"
  INFO:  "item_images": scanned 0 of 0 pages, containing 0 live rows and 0 dead rows; 0 rows in sample, 0 estimated total rows
  VACUUM
  ```

### DBA Justification on Role Permissions & Exclusion of `VACUUM FULL`
- **Role Permission Boundary:** `VACUUM` requires table ownership. Attempting it under `lostandfound_app` resulted in a permission denial (`WARNING: permission denied to vacuum "items", skipping it`), confirming that routine maintenance must be orchestrated by a designated maintenance or administrative account (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites entire table files and acquires an `AccessExclusiveLock`, blocking concurrent SELECT, INSERT, and UPDATE queries. Because PUP-SPOT tables contain modest row counts fitting inside single 8 kB pages, standard non-blocking `VACUUM` reclaimed all dead tuples (`1 removed` in `items`, `1 removed` in `item_images`) and truncated empty pages without application downtime.

---

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Public Filter by Category & Status with Recency Sort
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT i.item_id, c.name AS category_name, i.title, i.location_found, i.date_found
FROM items i
JOIN categories c ON i.category_id = c.category_id
WHERE i.status = 'unclaimed'
ORDER BY i.date_found DESC;
```

#### Actual Execution Plan Output
```text
                                                       QUERY PLAN                                                       
------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=2.37..2.38 rows=4 width=72) (actual time=0.062..0.062 rows=4.00 loops=1)
   Sort Key: i.date_found DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=5
   ->  Hash Join  (cost=1.14..2.33 rows=4 width=72) (actual time=0.038..0.040 rows=4.00 loops=1)
         Hash Cond: (c.category_id = i.category_id)
         Buffers: shared hit=2
         ->  Seq Scan on categories c  (cost=0.00..1.11 rows=11 width=22) (actual time=0.010..0.010 rows=11.00 loops=1)
               Buffers: shared hit=1
         ->  Hash  (cost=1.09..1.09 rows=4 width=58) (actual time=0.020..0.020 rows=4.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on items i  (cost=0.00..1.09 rows=4 width=58) (actual time=0.010..0.011 rows=4.00 loops=1)
                     Filter: ((status)::text = 'unclaimed'::text)
                     Rows Removed by Filter: 3
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=223
 Planning Time: 4.923 ms
 Execution Time: 0.089 ms
(20 rows)
```

#### Interpretation
1. **Execution Time:** `0.089 ms` (ultra-fast sub-millisecond execution).
2. **Buffer Efficiency:** `shared hit = 5`, with 0 disk block reads (100% in-memory cache hit).
3. **Sequential Scan Justification:** Even if an index were added on `status`, the PostgreSQL query planner deliberately chose a `Seq Scan` on `items` and `categories`. Because the entire table fits inside a single 8 kB heap block (`Buffers: shared hit = 1`), sequentially scanning that block in memory is computationally cheaper than loading the B-Tree index page followed by random pointer lookups into the heap.

---

### Query 2: Multi-Table Match Verification Query (Staff Review)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT pm.match_id, pm.similarity_score, lr.report_code, lr.title AS lost_title, 
       i.item_id, i.title AS found_title, i.status AS item_status
FROM potential_matches pm
JOIN lost_reports lr ON pm.report_id = lr.report_id
JOIN items i ON pm.item_id = i.item_id
WHERE pm.similarity_score >= 50.00
ORDER BY pm.similarity_score DESC;
```

#### Actual Execution Plan Output
```text
                                                             QUERY PLAN                                                              
-------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=3.54..3.56 rows=6 width=85) (actual time=0.094..0.095 rows=6.00 loops=1)
   Sort Key: pm.similarity_score DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=6
   ->  Hash Join  (cost=2.34..3.47 rows=6 width=85) (actual time=0.063..0.066 rows=6.00 loops=1)
         Hash Cond: (pm.item_id = i.item_id)
         Buffers: shared hit=3
         ->  Hash Join  (cost=1.18..2.28 rows=6 width=44) (actual time=0.036..0.039 rows=6.00 loops=1)
               Hash Cond: (pm.report_id = lr.report_id)
               Buffers: shared hit=2
               ->  Seq Scan on potential_matches pm  (cost=0.00..1.07 rows=6 width=18) (actual time=0.008..0.009 rows=6.00 loops=1)
                     Filter: (similarity_score >= 50.00)
                     Buffers: shared hit=1
               ->  Hash  (cost=1.08..1.08 rows=8 width=34) (actual time=0.011..0.011 rows=8.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on lost_reports lr  (cost=0.00..1.08 rows=8 width=34) (actual time=0.006..0.007 rows=8.00 loops=1)
                           Buffers: shared hit=1
         ->  Hash  (cost=1.07..1.07 rows=7 width=45) (actual time=0.021..0.021 rows=7.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on items i  (cost=0.00..1.07 rows=7 width=45) (actual time=0.013..0.015 rows=7.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=280
 Planning Time: 3.706 ms
 Execution Time: 0.141 ms
(27 rows)
```

#### Interpretation
1. **Execution Time:** `0.141 ms`.
2. **Buffer Hits:** `shared hit = 6` (100% cache hit, 0 physical disk reads).
3. **Join Strategy:** The query planner built in-memory hash tables for `lost_reports` (9 kB) and `items` (9 kB), executing two chained `Hash Joins` with zero disk spills (`Batches: 1`).

---

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current schema indexes | Primary key and unique indexes exist on all 8 tables. Current queries execute in < 0.15 ms with 100% cache hits. Additional indexes would introduce unnecessary insert overhead during report filings without improving latency. |
| **Follow-up Action** | Add Foreign Key Indexes for Scaled Incident Data | When the database grows past 5,000 lost items and claims, foreign key lookups and cascading deletions will benefit from B-Tree indexes: `CREATE INDEX idx_items_category_id ON items(category_id);`, `CREATE INDEX idx_claims_item_id ON claims(item_id);`, and `CREATE INDEX idx_potential_matches_pair ON potential_matches(report_id, item_id);`. |
| **Follow-up Action** | Automated Maintenance Script via Task Scheduler | Deploy a scheduled weekly maintenance job invoking `VACUUM (ANALYZE)` during off-peak hours (e.g. Sunday 02:00 AM) to automatically reclaim space from updated claim statuses and temporary image uploads. |

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