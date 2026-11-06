# Activity 5 Technical Evidence Dossier - Group 06: HabalGo: Ride and Booking Management System
## COMP 018: Database Maintenance & Monitoring Technical Evidence Audit

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Charles Kean E., Infantado, Gerwin G., Pocsidio, Jhon Louie F., Abangan, SJ Kaizen M., Bartolata, Redge Raindel N. |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **HabalGo: Ride and Booking Management System** |
| **Target Project Group** | **Group 6:** Barte, Roxanne; Balansay, Bohdana Yoome; Gumahin, Jewey Kristine; Remoquillo, Michelin; Royo, Jeana; Villareal, Mae Angeline |
| **Target Project Repository** | [https://github.com/jeanaroyo/HabalGo](https://github.com/jeanaroyo/HabalGo) |
| **Primary Database** | `grp00_habalgodb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Target Schema** | `habalgo` |
| **Restored Test Database** | `grp00_habalgodb_restore_test` |

---

# EVIDENCE DOSSIER & VERIFICATION ARTIFACTS

This evidence dossier contains the unedited diagnostic queries, terminal execution outputs, log entries, and performance plans executed during Activity 5 for **Group 06 (HabalGo: Ride and Booking Management System)**.

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

-- 4. Table Inventory and Individual Table Sizes (Schema: habalgo)
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident('habalgo') || '.' || quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident('habalgo') || '.' || quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident('habalgo') || '.' || quote_ident(table_name))) AS index_size
FROM information_schema.tables
WHERE table_schema = 'habalgo' AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident('habalgo') || '.' || quote_ident(table_name)) DESC;

-- 5. Total Number of Tables and Sequences
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'habalgo') AS total_tables,
    (SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'habalgo') AS total_sequences;
```

### Actual Output
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Current Database & User:** `grp00_habalgodb`, `postgres`
- **Total Database Size:** `8662 kB` (~8.66 MB)
- **Object Count:** `total_tables: 8`, `total_sequences: 7`
- **Table Storage Breakdown:**
  ```text
     table_name    | total_size | table_size | index_size 
  -----------------+------------+------------+------------
   booking         | 72 kB      | 8192 bytes | 64 kB
   rating_feedback | 64 kB      | 8192 bytes | 48 kB
   payment         | 40 kB      | 8192 bytes | 32 kB
   driver          | 40 kB      | 8192 bytes | 32 kB
   vehicle         | 40 kB      | 8192 bytes | 32 kB
   customer        | 40 kB      | 8192 bytes | 32 kB
   driver_vehicle  | 24 kB      | 8192 bytes | 16 kB
   location        | 24 kB      | 8192 bytes | 16 kB
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
```

### Actual Output
- **Database Statistics:**
  ```text
       datname     | open_connections | committed_transactions | rolled_back_transactions | conflicts | deadlocks 
  -----------------+------------------+------------------------+--------------------------+-----------+-----------
   grp00_habalgodb |                1 |                    109 |                       13 |         0 |         0
  ```
  *Analysis:* `conflicts = 0`, `deadlocks = 0`. The database exhibits zero locking contention. Rolled-back transactions reflect intentional permission denial tests from Activity 4.
- **Active Session Inspection:** Only the monitoring administrative `psql` connection was active, with no stalled, long-running, or idle-in-transaction connections.

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
WHERE schemaname = 'habalgo'
ORDER BY n_dead_tup DESC, relname;
```

### Actual Output (Pre-Maintenance State)
```text
   table_name    | sequential_scans | tuples_read_by_seq_scan | index_scans | live_tuples | dead_tuples | last_vacuum | last_autovacuum | last_analyze | last_autoanalyze 
-----------------+------------------+-------------------------+-------------+-------------+-------------+-------------+-----------------+--------------+------------------
 driver          |                6 |                      24 |          27 |          10 |          17 |             |                 |              | 
 vehicle         |                6 |                       8 |          11 |           4 |           8 |             |                 |              | 
 driver_vehicle  |                3 |                       3 |           4 |           3 |           7 |             |                 |              | 
 booking         |                6 |                       5 |           9 |           6 |           1 |             |                 |              | 
 customer        |                4 |                       4 |          15 |           2 |           0 |             |                 |              | 
 location        |                3 |                       6 |          12 |           6 |           0 |             |                 |              | 
 payment         |                4 |                       4 |           1 |           4 |           0 |             |                 |              | 
 rating_feedback |                5 |                       2 |           0 |           2 |           0 |             |                 |              | 
(8 rows)
```
*Analysis:* 
- `driver` accumulated 17 dead tuples, `vehicle` accumulated 8 dead tuples, and `driver_vehicle` accumulated 7 dead tuples due to sequence resets, aborted batch inserts, and temporary assignment testing.
- `last_vacuum` and `last_analyze` were `NULL`, confirming that routine maintenance was required to clean dead tuple bloat and refresh planner statistics.

---

---

## 2.4 Log Review & Issue Identification

### PostgreSQL Log Analysis (`C:\Program Files\PostgreSQL\18\data\log\postgresql-2026-11-05_000000.log`)
1. **Activity 4 Access Control Logging:**
   - **Log Entry:** `ERROR: role "app_runtime_grp" already exists` during initial script execution.
   - **Log Entry:** `ERROR: permission denied for table booking on statement DELETE FROM habalgo.booking WHERE booking_id = 6;` by `habalgo_app_user`
   - **Log Entry:** `ERROR: permission denied for table payment on statement DELETE FROM habalgo.payment WHERE payment_id = 1;` by `habalgo_app_user`
   - **Log Entry:** `ERROR: must be owner of table rating_feedback on statement DROP TABLE habalgo.rating_feedback;` by `habalgo_app_user`
   - **Log Entry:** `ERROR: permission denied for table location on statement INSERT INTO habalgo.location (address, city) VALUES ('Rogue Terminal', 'Quezon City');` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied for table payment on statement UPDATE habalgo.payment SET status = 'Refunded' WHERE payment_id = 1;` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied for table booking on statement DELETE FROM habalgo.booking WHERE booking_id = 1;` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied for table rating_feedback on statement TRUNCATE TABLE habalgo.rating_feedback;` by `habalgo_readonly_user`
   - **Log Entry:** `ERROR: permission denied to create database on statement CREATE DATABASE rogue_habalgo_db;` by `habalgo_app_user`
   - **Log Entry:** `FATAL: permission denied for database "canteen_db" DETAIL: User does not have CONNECT privilege.` on connection by `habalgo_app_user`
   - **Log Entry:** `FATAL: permission denied for database "grp07_campussafedb" DETAIL: User does not have CONNECT privilege.` on connection by `habalgo_app_user`
   - **Assessment:** Positive validation of security controls. The PostgreSQL engine successfully intercepted and recorded every unauthorized operation.
2. **Server Health:** Engine checkpoints and background writer threads completed with 0 errors.

---

---

## 2.5 Routine Maintenance Operations (`ANALYZE` & `VACUUM`)

### Maintenance Execution & Role Permission Verification
- **Application User Permission Boundary Test:** Attempting to execute `VACUUM habalgo.booking;` under `habalgo_app_user` produced:
  ```text
  WARNING:  permission denied to vacuum "booking", skipping it
  VACUUM
  ```
  This proves that application runtime accounts cannot run table maintenance. Maintenance operations require table ownership or superuser status (`postgres`).

- **Standalone Maintenance Commands Executed as `postgres`:**
  ```sql
  ANALYZE;
  VACUUM (VERBOSE, ANALYZE) habalgo.driver;
  VACUUM (VERBOSE, ANALYZE) habalgo.vehicle;
  VACUUM (VERBOSE, ANALYZE) habalgo.driver_vehicle;
  VACUUM (VERBOSE, ANALYZE) habalgo.booking;
  ```

### Actual Output
- **`ANALYZE;` Output:** `ANALYZE`
- **`VACUUM (VERBOSE, ANALYZE) habalgo.driver;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.driver"
  INFO:  finished vacuuming "grp00_habalgodb.habalgo.driver": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 17 removed, 10 remain, 0 are dead but not yet removable
  removable cutoff: 2102, which was 0 XIDs old when operation ended
  new relfrozenxid: 2022, which is 12 XIDs ahead of previous value
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 1 pages from table (100.00% of total) had 17 dead item identifiers removed
  index "driver_pkey": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  index "driver_license_no_key": pages: 2 in total, 0 newly deleted, 0 currently deleted, 0 reusable
  buffer usage: 86 hits, 0 reads, 6 dirtied
  WAL usage: 9 records, 6 full page images, 34160 bytes, 0 buffers full
  INFO:  analyzing "habalgo.driver"
  INFO:  "driver": scanned 1 of 1 pages, containing 10 live rows and 0 dead rows; 10 rows in sample, 10 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) habalgo.vehicle;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.vehicle"
  INFO:  finished vacuuming "grp00_habalgodb.habalgo.vehicle": index scans: 1
  pages: 0 removed, 1 remain, 1 scanned (100.00% of total), 0 eagerly scanned
  tuples: 8 removed, 4 remain, 0 are dead but not yet removable
  visibility map: 1 pages set all-visible, 0 pages set all-frozen (0 were all-visible)
  index scan needed: 1 pages from table (100.00% of total) had 8 dead item identifiers removed
  buffer usage: 86 hits, 0 reads, 7 dirtied
  INFO:  analyzing "habalgo.vehicle"
  INFO:  "vehicle": scanned 1 of 1 pages, containing 4 live rows and 0 dead rows; 4 rows in sample, 4 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) habalgo.driver_vehicle;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.driver_vehicle"
  tuples: 7 removed, 3 remain, 0 are dead but not yet removable
  buffer usage: 65 hits, 0 reads, 7 dirtied
  INFO:  analyzing "habalgo.driver_vehicle"
  INFO:  "driver_vehicle": scanned 1 of 1 pages, containing 3 live rows and 0 dead rows; 3 rows in sample, 3 estimated total rows
  VACUUM
  ```
- **`VACUUM (VERBOSE, ANALYZE) habalgo.booking;` Output:**
  ```text
  INFO:  vacuuming "grp00_habalgodb.habalgo.booking"
  tuples: 1 removed, 6 remain, 0 are dead but not yet removable
  buffer usage: 94 hits, 0 reads, 6 dirtied
  INFO:  analyzing "habalgo.booking"
  INFO:  "booking": scanned 1 of 1 pages, containing 6 live rows and 0 dead rows; 6 rows in sample, 6 estimated total rows
  VACUUM
  ```

### Post-Maintenance Statistics Verification
```sql
SELECT 
    relname AS table_name,
    n_live_tup AS live_tuples,
    n_dead_tup AS dead_tuples,
    last_vacuum, last_analyze
FROM pg_stat_user_tables
WHERE schemaname = 'habalgo'
ORDER BY relname;
```

**Actual Output:**
```text
   table_name    | live_tuples | dead_tuples |          last_vacuum          |         last_analyze          
-----------------+-------------+-------------+-------------------------------+-------------------------------
 booking         |           6 |           0 | 2026-11-05 06:45:57.932108-05 | 2026-11-05 06:45:57.93683-05
 customer        |           2 |           0 |                               | 2026-11-05 06:45:09.650616-05
 driver          |          10 |           0 | 2026-11-05 06:45:16.605773-05 | 2026-11-05 06:45:16.60886-05
 driver_vehicle  |           3 |           0 | 2026-11-05 06:45:46.193784-05 | 2026-11-05 06:45:46.195847-05
 location        |           6 |           0 |                               | 2026-11-05 06:45:09.641533-05
 payment         |           4 |           0 |                               | 2026-11-05 06:45:09.645095-05
 rating_feedback |           2 |           0 |                               | 2026-11-05 06:45:09.643518-05
 vehicle         |           4 |           0 | 2026-11-05 06:45:35.625139-05 | 2026-11-05 06:45:35.629053-05
(8 rows)
```
*Outcome:* All dead tuples across all tables were reduced to 0, reclamation was complete, and planner statistics were fully refreshed.

### DBA Justification: Role Permissions & Exclusion of `VACUUM FULL`
- **Permission Scope:** `VACUUM` modifies physical heap blocks and requires table ownership. Running it under application accounts violates the principle of least privilege and triggers permission warnings. Maintenance must be scheduled and executed by a dedicated DBA role (`postgres`).
- **Exclusion of `VACUUM FULL`:** `VACUUM FULL` rewrites the table into a new disk file and acquires an `AccessExclusiveLock`, completely blocking concurrent `SELECT`, `INSERT`, `UPDATE`, and `DELETE` queries. For HabalGo, standard non-blocking `VACUUM` reclaimed 100% of the dead tuples (`17 removed` in `driver`, `8 removed` in `vehicle`, `7 removed` in `driver_vehicle`, and `1 removed` in `booking`) without causing any application downtime.

---

---

## 2.6 Query Performance Analysis (`EXPLAIN` & `EXPLAIN (ANALYZE, BUFFERS)`)

### Query 1: Bookings List Multi-Table Join (`api.php` line 45-47 `$BK`)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    b.booking_id AS id,
    b.customer_id,
    c.name AS customer,
    lp.address AS pickup,
    lp.city AS pickup_city,
    ld.address AS dropoff,
    ld.city AS dropoff_city,
    b.driver_id,
    d.name AS driver,
    b.booking_status,
    to_char(b.booking_date, 'Mon DD') AS date
FROM habalgo.booking b
JOIN habalgo.customer c ON c.customer_id = b.customer_id
JOIN habalgo.location lp ON lp.location_id = b.pickup_location_id
JOIN habalgo.location ld ON ld.location_id = b.dropoff_location_id
LEFT JOIN habalgo.driver d ON d.driver_id = b.driver_id
ORDER BY b.booking_id DESC;
```

#### Actual Execution Plan Output
```text
                                                                    QUERY PLAN                                                                     
---------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=5.85..5.86 rows=6 width=149) (actual time=0.835..0.839 rows=6.00 loops=1)
   Sort Key: b.booking_id DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=8
   ->  Hash Join  (cost=4.48..5.77 rows=6 width=149) (actual time=0.802..0.817 rows=6.00 loops=1)
         Hash Cond: (b.dropoff_location_id = ld.location_id)
         Buffers: shared hit=5
         ->  Hash Join  (cost=3.34..4.59 rows=6 width=96) (actual time=0.054..0.066 rows=6.00 loops=1)
               Hash Cond: (b.customer_id = c.customer_id)
               Buffers: shared hit=4
               ->  Hash Right Join  (cost=2.30..3.50 rows=6 width=82) (actual time=0.040..0.049 rows=6.00 loops=1)
                     Hash Cond: (d.driver_id = b.driver_id)
                     Buffers: shared hit=3
                     ->  Seq Scan on driver d  (cost=0.00..1.10 rows=10 width=19) (actual time=0.005..0.006 rows=10.00 loops=1)
                           Buffers: shared hit=1
                     ->  Hash  (cost=2.22..2.22 rows=6 width=67) (actual time=0.027..0.029 rows=6.00 loops=1)
                           Buckets: 1024  Batches: 1  Memory Usage: 9kB
                           Buffers: shared hit=2
                           ->  Hash Join  (cost=1.14..2.22 rows=6 width=67) (actual time=0.021..0.024 rows=6.00 loops=1)
                                 Hash Cond: (b.pickup_location_id = lp.location_id)
                                 Buffers: shared hit=2
                                 ->  Seq Scan on booking b  (cost=0.00..1.06 rows=6 width=38) (actual time=0.007..0.008 rows=6.00 loops=1)
                                       Buffers: shared hit=1
                                 ->  Hash  (cost=1.06..1.06 rows=6 width=37) (actual time=0.006..0.006 rows=6.00 loops=1)
                                       Buckets: 1024  Batches: 1  Memory Usage: 9kB
                                       Buffers: shared hit=1
                                       ->  Seq Scan on location lp  (cost=0.00..1.06 rows=6 width=37) (actual time=0.002..0.003 rows=6.00 loops=1)
                                             Buffers: shared hit=1
               ->  Hash  (cost=1.02..1.02 rows=2 width=18) (actual time=0.010..0.011 rows=2.00 loops=1)
                     Buckets: 1024  Batches: 1  Memory Usage: 9kB
                     Buffers: shared hit=1
                     ->  Seq Scan on customer c  (cost=0.00..1.02 rows=2 width=18) (actual time=0.004..0.005 rows=2.00 loops=1)
                           Buffers: shared hit=1
         ->  Hash  (cost=1.06..1.06 rows=6 width=37) (actual time=0.021..0.022 rows=6.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on location ld  (cost=0.00..1.06 rows=6 width=37) (actual time=0.014..0.014 rows=6.00 loops=1)
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=292
 Planning Time: 5.634 ms
 Execution Time: 0.963 ms
(42 rows)
```

#### Interpretation
1. **Execution Time:** `0.963 ms` (sub-millisecond latency).
2. **Buffer Hit Ratio:** `shared hit = 8`, with 0 disk block reads (100% cache hit).
3. **Execution Strategy:** Chained in-memory hash joins across `booking`, `location` (pickup), `driver`, `customer`, and `location` (dropoff), followed by an in-memory quicksort on `booking_id DESC` requiring only 25 kB of RAM. The planner appropriately utilized sequential scans over small heap blocks rather than traversing multi-level B-Tree index pages.

---

### Query 2: Dashboard Metrics Aggregation (`api.php` line 66-70)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    (SELECT count(*) FROM habalgo.customer)::int AS customers,
    (SELECT count(*) FROM habalgo.booking WHERE booking_status IN ('Pending','Confirmed','In Progress'))::int AS active,
    (SELECT COALESCE(sum(amount),0) FROM habalgo.payment WHERE status='Paid')::float AS revenue,
    (SELECT round(avg(rating),1) FROM habalgo.rating_feedback)::float AS avg_rating;
```

#### Actual Execution Plan Output
```text
                                                        QUERY PLAN                                                        
--------------------------------------------------------------------------------------------------------------------------
 Result  (cost=4.25..4.27 rows=1 width=24) (actual time=0.363..0.365 rows=1.00 loops=1)
   Buffers: shared hit=4
   InitPlan 1
     ->  Aggregate  (cost=1.02..1.03 rows=1 width=8) (actual time=0.023..0.024 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on customer  (cost=0.00..1.02 rows=2 width=0) (actual time=0.018..0.018 rows=2.00 loops=1)
                 Buffers: shared hit=1
   InitPlan 2
     ->  Aggregate  (cost=1.09..1.10 rows=1 width=8) (actual time=0.022..0.022 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on booking  (cost=0.00..1.08 rows=4 width=0) (actual time=0.020..0.021 rows=4.00 loops=1)
                 Filter: ((booking_status)::text = ANY ('{Pending,Confirmed,"In Progress"}'::text[]))
                 Rows Removed by Filter: 2
                 Buffers: shared hit=1
   InitPlan 3
     ->  Aggregate  (cost=1.06..1.07 rows=1 width=32) (actual time=0.028..0.029 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on payment  (cost=0.00..1.05 rows=3 width=5) (actual time=0.010..0.011 rows=3.00 loops=1)
                 Filter: ((status)::text = 'Paid'::text)
                 Rows Removed by Filter: 1
                 Buffers: shared hit=1
   InitPlan 4
     ->  Aggregate  (cost=1.03..1.04 rows=1 width=32) (actual time=0.254..0.254 rows=1.00 loops=1)
           Buffers: shared hit=1
           ->  Seq Scan on rating_feedback  (cost=0.00..1.02 rows=2 width=4) (actual time=0.030..0.030 rows=2.00 loops=1)
                 Buffers: shared hit=1
 Planning:
   Buffers: shared hit=301
 Planning Time: 9.232 ms
 Execution Time: 0.426 ms
(30 rows)
```

#### Interpretation
1. **Execution Time:** `0.426 ms`.
2. **Buffer Efficiency:** `shared hit = 4`, 0 disk reads.
3. **Execution Strategy:** Four independent `InitPlans` executed in memory to calculate total customers, active rides, revenue sum, and average rating, consolidating the scalar values into a single `Result` row in 0.426 ms.

---

---

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current schema indexes | All 5 custom indexes (`idx_booking_customer`, `idx_booking_date`, `idx_booking_driver`, `idx_payment_booking`, `idx_rating_booking`) and 12 constraint indexes are properly configured. Query execution latencies are < 1.0 ms with 100% cache hits. Additional indexes would create write overhead during booking insertions without improving latency. |
| **Follow-up Action** | Partial Composite Index on Active Bookings | As the platform scales past 10,000 completed rides in production, the dashboard query filtering active bookings (`WHERE booking_status IN ('Pending','Confirmed','In Progress')`) will benefit from a partial index: `CREATE INDEX idx_booking_active ON habalgo.booking (booking_status) WHERE booking_status IN ('Pending','Confirmed','In Progress');`. |
| **Follow-up Action** | Scheduled Maintenance via Windows Task Scheduler | Deploy an automated weekly maintenance script invoking `VACUUM (ANALYZE)` during low-traffic windows (e.g., Sunday 03:00 AM) to maintain 0 dead tuples as high-velocity bookings update. |

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