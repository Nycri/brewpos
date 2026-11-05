# COMP 018: Database Administration — Comprehensive Project Report
## Activities 4, 5, and 6: Access Control, Maintenance & Monitoring, Backup & Recovery

---

### Project Identification & Metadata

| Field | Details |
|---|---|
| **Course** | COMP 018 — Database Administration |
| **Evaluating Group** | **Group 4:** Olivarez, Abangan, Pocsidio, Infantado, and Bartolata |
| **Evaluating Group Repository** | [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos) |
| **Target Project Monitored** | **PUP-SPOT: Campus Lost and Found System** |
| **Target Project Group** | **Group 5:** Imperial, Manansala, Napeñas, Lctz, Quody |
| **Target Project Repository** | [https://github.com/grp05-dba/grp05_lostandfound](https://github.com/grp05-dba/grp05_lostandfound) |
| **Primary Database** | `grp05_lostandfounddb` (PostgreSQL 18.6 on x86_64 Windows, compiled by MSVC) |
| **Restored Test Database** | `grp05_lostandfounddb_restore_test` |

---

# TABLE OF CONTENTS
1. [ACTIVITY 4: Database Users, Roles, Privileges, and Access Control](#activity-4-database-users-roles-privileges-and-access-control)
   - 1.1 Access-Control Plan & Architecture
   - 1.2 SQL Scripts Summary & Setup Execution
   - 1.3 Verification Results: Test Suite 1 (Runtime Admin User `lostandfound_app`)
   - 1.4 Verification Results: Test Suite 2 (Public User `lostandfound_public`)
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
   - 3.3 Restoration into Separate Test Database (`grp05_lostandfounddb_restore_test`)
   - 3.4 Post-Restore Verification Suite
   - 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)
   - 3.6 Limitations, Evaluation & Production Recommendations
4. [CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES](#consolidated-class-google-sheets-tracker-entries)
5. [AI USAGE DECLARATION](#ai-usage-declaration)

---

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Activity 3
In many default configurations and during initial prototype setups, applications connect using the PostgreSQL superuser (`postgres`) or an unrestricted table owner account. In the case of PUP-SPOT, the application codebase relies on two separate interfaces: a public-facing portal for students and guests, and an administrative portal for Campus Marshals and staff. However, in the default `.env` configuration, `DB_USER=postgres` was specified. Connecting an interactive web application using a database superuser account severely violates the **Principle of Least Privilege (PoLP)**. If an attacker or unauthenticated student were to discover a SQL injection vulnerability or abuse a public endpoint, they would possess unrestricted DDL and DML capabilities—allowing them to drop legal claim histories, truncate item inventories, alter contact records, or compromise cluster integrity.

### Activity 4 Solution & Role Hierarchy
For Group 5's **PUP-SPOT** platform, we implemented a comprehensive Role-Based Access Control (RBAC) hierarchy decoupling administrative maintenance from operational application traffic, while strictly separating public student actions from staff actions:
- **Group Roles (`NOLOGIN`):** Architectural privilege templates that define specific capability boundaries (`lostandfound_app_grp`, `lostandfound_public_grp`, and `lostandfound_backup_grp`).
- **Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their respective group roles (`lostandfound_app`, `lostandfound_public`, and `lostandfound_backup`).
- **Administrative Application Role (`lostandfound_app`):** The staff/marshal runtime account is granted `SELECT`, `INSERT`, and `UPDATE` on all operational tables (`categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, `lost_report_images`, `claim_images`). Crucially, its `DELETE` privilege is strictly scoped to `potential_matches`, `item_images`, `lost_report_images`, and `claim_images` (allowing staff to remove false automated matches or delete misfiled image attachments). It is **strictly denied `DELETE` on `items`, `lost_reports`, `claims`, and `categories`**, guaranteeing that core incident tracking and legal claim audits cannot be purged through runtime API calls.
- **Public User Role (`lostandfound_public`):** The student/guest account is granted `SELECT` on `categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, and `lost_report_images` to allow browsing found items and tracking submissions. It is granted `INSERT` on `lost_reports`, `claims`, `potential_matches`, `lost_report_images`, and `claim_images` to submit lost reports and claim requests. It is granted column-level `UPDATE (status)` strictly on `lost_reports` (allowing reporters to cancel active reports), and `DELETE` strictly on `potential_matches`. It is **strictly denied `DELETE` or `UPDATE` on `items`, `claims`, and `categories`**, and **strictly denied `INSERT` on `categories` and `items`**.
- **Dedicated Backup Role (`lostandfound_backup`):** Logical backups are decoupled from application accounts and delegated to `lostandfound_backup`, a read-only account possessing schema-wide `SELECT` on all tables and sequences without write, update, or schema alteration permissions.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `lostandfound_app` | `lostandfound_app_grp` | Campus Marshal / Staff Admin Runtime | Manages found-item catalog, reviews claim requests, updates administrative statuses, and removes invalid image attachments | • `CONNECT` on `grp05_lostandfounddb`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on all 8 tables<br>• `DELETE` **strictly on `potential_matches`, `item_images`, `lost_report_images`, `claim_images`**<br>• `USAGE, SELECT` on all sequences | Restricting `DELETE` strictly to matches and image attachments ensures that core lost-item inventories, filed claims, and category definitions cannot be deleted through runtime API calls. |
| `lostandfound_public` | `lostandfound_public_grp` | Student / Guest Public Portal | Browses found items, searches categories, submits lost reports, files claim requests, and tracks status via reference codes | • `CONNECT` on `grp05_lostandfounddb`<br>• `USAGE` on schema `public`<br>• `SELECT` on `categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, `lost_report_images`<br>• `INSERT` on `lost_reports`, `claims`, `potential_matches`, `lost_report_images`, `claim_images`<br>• `UPDATE (status)` strictly on `lost_reports`<br>• `DELETE` strictly on `potential_matches`<br>• `USAGE, SELECT` on required sequences | Enables public users to browse and file reports while strictly preventing them from modifying found-item descriptions, altering claim decisions, tampering with categories, or deleting submitted records. |
| `lostandfound_backup` | `lostandfound_backup_grp` | Backup Operator | Runs logical database backups (`pg_dump`) | • `CONNECT` on `grp05_lostandfounddb`<br>• `USAGE` on schema `public`<br>• `SELECT` on all tables & sequences | Grants complete read access required for logical dumps while strictly preventing DDL or DML write operations. |
| `postgres` | *(Superuser)* | Database Administrator | Schema provisioning, migrations, maintenance (`VACUUM`/`ANALYZE`), disaster recovery | Superuser / Owner privileges | Reserved strictly for DBA operations; completely removed from interactive application connection pools. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The access-control configuration script was executed as `postgres` against `grp05_lostandfounddb`:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE lostandfound_app_grp NOLOGIN;
CREATE ROLE lostandfound_public_grp NOLOGIN;
CREATE ROLE lostandfound_backup_grp NOLOGIN;

-- 2. Create Login Roles (LOGIN)
CREATE ROLE lostandfound_app LOGIN PASSWORD 'AppRuntimePass2026!';
CREATE ROLE lostandfound_public LOGIN PASSWORD 'PublicUserPass2026!';
CREATE ROLE lostandfound_backup LOGIN PASSWORD 'BackupOperatorPass2026!';

-- 3. Assign Role Memberships
GRANT lostandfound_app_grp TO lostandfound_app;
GRANT lostandfound_public_grp TO lostandfound_public;
GRANT lostandfound_backup_grp TO lostandfound_backup;

-- 4. Lock down PUBLIC permissions
REVOKE ALL ON DATABASE grp05_lostandfounddb FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 5. Database Connection and Schema Usage
GRANT CONNECT ON DATABASE grp05_lostandfounddb TO lostandfound_app_grp, lostandfound_public_grp, lostandfound_backup_grp;
GRANT USAGE ON SCHEMA public TO lostandfound_app_grp, lostandfound_public_grp, lostandfound_backup_grp;

-- 6. Application Admin Runtime Permissions (Principle of Least Privilege)
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO lostandfound_app_grp;
GRANT DELETE ON potential_matches, item_images, lost_report_images, claim_images TO lostandfound_app_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO lostandfound_app_grp;

-- 7. Public User Permissions (Principle of Least Privilege)
GRANT SELECT ON categories, items, lost_reports, claims, potential_matches, item_images, lost_report_images TO lostandfound_public_grp;
GRANT INSERT ON lost_reports, claims, potential_matches, lost_report_images, claim_images TO lostandfound_public_grp;
GRANT UPDATE (status) ON lost_reports TO lostandfound_public_grp;
GRANT DELETE ON potential_matches TO lostandfound_public_grp;
GRANT USAGE, SELECT ON SEQUENCE
    lost_reports_report_id_seq,
    claims_claim_id_seq,
    potential_matches_match_id_seq,
    lost_report_images_report_image_id_seq,
    claim_images_claim_image_id_seq
TO lostandfound_public_grp;

-- 8. Backup Operator Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO lostandfound_backup_grp;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO lostandfound_backup_grp;

-- 9. Default Privileges for Future Tables & Sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO lostandfound_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO lostandfound_app_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO lostandfound_backup_grp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO lostandfound_backup_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime Admin User `lostandfound_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SELECT item_id, title, status FROM items LIMIT 5;
  ```
- **Expected Result:** Allowed; returns registered lost/found items.
- **Actual Result / Output:**
  ```text
   item_id |               title                |  status   
  ---------+------------------------------------+-----------
         1 | [DEMO] Black leather wallet        | unclaimed
         2 | [DEMO] Set of three silver keys    | unclaimed
         3 | [DEMO] Navy blue backpack          | claimed
         4 | [DEMO] Casio scientific calculator | unclaimed
         5 | [DEMO] Foldable black umbrella     | archived
  (5 rows)
  ```
- **Status:** PASS

### Test 1.2: Insert an Item & Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO items (category_id, title, description, location_found, date_found, status)
  VALUES (1, 'Activity 4 Test Item: Lost Student ID Card', 'Found near library entrance', 'Library Ground Floor', '2026-11-05', 'unclaimed')
  RETURNING item_id, title, status;
  ```
- **Expected Result:** Allowed; inserts record and consumes sequence value.
- **Actual Result / Output:**
  ```text
   item_id |                   title                    |  status   
  ---------+--------------------------------------------+-----------
         7 | Activity 4 Test Item: Lost Student ID Card | unclaimed
  (1 row)
  INSERT 0 1
  ```
- **Status:** PASS

### Test 1.3: Update Record (Allowed)
- **SQL Statement:**
  ```sql
  UPDATE items SET status = 'claimed' WHERE item_id = 7 RETURNING item_id, status;
  ```
- **Expected Result:** Allowed; runtime admin updates item lifecycle status.
- **Actual Result / Output:**
  ```text
   item_id | status  
  ---------+---------
         7 | claimed
  (1 row)
  UPDATE 1
  ```
- **Status:** PASS

### Test 1.4: Delete Image Attachment (Allowed — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  INSERT INTO item_images (item_id, storage_key, mime_type)
  VALUES (7, 'temp_item_image_test.jpg', 'image/jpeg')
  RETURNING item_image_id;
  DELETE FROM item_images WHERE storage_key = 'temp_item_image_test.jpg';
  ```
- **Expected Result:** Allowed; admin role is authorized to remove misfiled image attachments.
- **Actual Result / Output:**
  ```text
   item_image_id 
  ---------------
               1
  (1 row)
  INSERT 0 1
  DELETE 1
  ```
- **Status:** PASS

### Test 1.5: Delete Claim Record (Denied — Narrow `DELETE` Enforcement)
- **SQL Statement:**
  ```sql
  DELETE FROM claims WHERE claim_id = 1;
  ```
- **Expected Result:** Denied; runtime admin account must never delete claim history.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table claims
  ```
- **Status:** PASS

### Test 1.6: Delete Category (Denied — Core Structure Protection)
- **SQL Statement:**
  ```sql
  DELETE FROM categories WHERE category_id = 1;
  ```
- **Expected Result:** Denied; admin account cannot purge standard system categories.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table categories
  ```
- **Status:** PASS

### Test 1.7: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  DROP TABLE item_images;
  ```
- **Expected Result:** Denied; DDL requires object ownership (`postgres`).
- **Actual Result / Output:**
  ```text
  ERROR:  must be owner of table item_images
  ```
- **Status:** PASS

---

## 1.4 Verification Results: Test Suite 2 (Public User `lostandfound_public`)

### Test 2.1: Read Items & Categories (Allowed)
- **SQL Statement:**
  ```sql
  SELECT i.item_id, c.name AS category, i.title, i.status 
  FROM items i 
  JOIN categories c ON i.category_id = c.category_id 
  LIMIT 5;
  ```
- **Expected Result:** Allowed; public users can browse active found items and categories.
- **Actual Result / Output:**
  ```text
   item_id |         category          |               title                |  status   
  ---------+---------------------------+------------------------------------+-----------
         1 | Wallets and Pouches       | [DEMO] Black leather wallet        | unclaimed
         2 | Keys and Keychains        | [DEMO] Set of three silver keys    | unclaimed
         3 | Bags                      | [DEMO] Navy blue backpack          | claimed
         4 | Books and School Supplies | [DEMO] Casio scientific calculator | unclaimed
         5 | Umbrellas                 | [DEMO] Foldable black umbrella     | archived
  (5 rows)
  ```
- **Status:** PASS

### Test 2.2: Submit Lost Report (Allowed)
- **SQL Statement:**
  ```sql
  INSERT INTO lost_reports (report_code, category_id, title, description, location_lost, date_lost, full_name, email, contact_preference, status)
  VALUES ('LR-ACT4-TEST01', 3, 'Activity 4 Lost Phone Test', 'Blue iPhone 13 in black case', 'East Wing 2nd Floor', '2026-11-05', 'Juan Dela Cruz', 'juan@example.com', 'email', 'active')
  RETURNING report_id, report_code, status;
  ```
- **Expected Result:** Allowed; public student can submit lost report and advance sequence.
- **Actual Result / Output:**
  ```text
   report_id |  report_code   | status 
  -----------+----------------+--------
           8 | LR-ACT4-TEST01 | active
  (1 row)
  INSERT 0 1
  ```
- **Status:** PASS

### Test 2.3: Attempt to Update Items (Denied — Tamper Prevention)
- **SQL Statement:**
  ```sql
  UPDATE items SET status = 'claimed' WHERE item_id = 1;
  ```
- **Expected Result:** Denied; public account has no UPDATE privilege on `items`.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table items
  ```
- **Status:** PASS

### Test 2.4: Attempt to Delete Item / Claim (Denied)
- **SQL Statement:**
  ```sql
  DELETE FROM items WHERE item_id = 1;
  ```
- **Expected Result:** Denied; public account cannot delete catalog items.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table items
  ```
- **Status:** PASS

### Test 2.5: Attempt to Insert into Categories (Denied)
- **SQL Statement:**
  ```sql
  INSERT INTO categories (name) VALUES ('Unauthorized Category');
  ```
- **Expected Result:** Denied; public account cannot create custom category entries.
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied for table categories
  ```
- **Status:** PASS

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  CREATE DATABASE rogue_test_db;
  ```
- **Expected Result:** Denied; application users must not possess administrative cluster privileges (`CREATEDB`).
- **Actual Result / Output:**
  ```text
  ERROR:  permission denied to create database
  ```
- **Status:** PASS

### Test 3.2: Attempt to Connect to Unrelated Database (Denied)
- **Command:**
  ```bash
  psql -U lostandfound_app -h localhost -p 5432 -d canteen_db -c "SELECT 1;"
  ```
- **Expected Result:** Denied; cross-database connection is blocked by revoked PUBLIC permissions.
- **Actual Result / Output:**
  ```text
  psql: error: connection to server at "localhost" (::1), port 5432 failed: FATAL:  permission denied for database "canteen_db"
  DETAIL:  User does not have CONNECT privilege.
  ```
- **Status:** PASS

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Superuser in Default Configuration:** In the initial `.env.example`, `DB_USER=postgres` was specified. We resolved this by defining the least-privilege role hierarchy (`lostandfound_app` for staff operations and `lostandfound_public` for public portal workflows) and synchronizing passwords in `.env`.
2. **Missing Multi-Statement Support in psql:** During routine maintenance execution, grouping `ANALYZE` and `VACUUM` into a single `-c` flag triggered `ERROR: VACUUM cannot run inside a transaction block`. This was addressed by executing maintenance statements as independent calls.
3. **Database File vs. Image Filesystem Synchronization:** Image metadata (`item_images`, `lost_report_images`, `claim_images`) stores only the storage key and MIME type, while actual image files are saved to the `storage/` directory. Restoring the database without corresponding files leaves broken links; this operational limitation was documented for backup planning.

### Feedback to Owning Group (Group 5: PUP-SPOT)
- **Commendation:** The relational schema design is remarkably clean and adheres to strong domain logic. The contact constraint (`CHECK (email IS NOT NULL OR phone_number IS NOT NULL)`), cascading foreign keys for item images, and distinct reference codes (`report_code`, `claim_code`) provide excellent integrity.
- **Recommendation 1 (Application-Level Authentication):** Currently, the administrative portal relies entirely on network-level IP isolation without an administrative user login system. Implementing an application-level staff authentication table with bcrypt hashes and session tokens will guard against unauthorized access within the campus local network.
- **Recommendation 2 (Unique Constraint on Potential Matches):** In `potential_matches`, the schema does not enforce a unique constraint on `(report_id, item_id)`. Adding `UNIQUE (report_id, item_id)` will prevent duplicate similarity records from being generated for the same pair.

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

## 2.7 Prioritized Recommendations & Technical Justification

| Priority | Action Item | Technical Justification |
|---|---|---|
| **No Action Required (Current)** | Retain current schema indexes | Primary key and unique indexes exist on all 8 tables. Current queries execute in < 0.15 ms with 100% cache hits. Additional indexes would introduce unnecessary insert overhead during report filings without improving latency. |
| **Follow-up Action** | Add Foreign Key Indexes for Scaled Incident Data | When the database grows past 5,000 lost items and claims, foreign key lookups and cascading deletions will benefit from B-Tree indexes: `CREATE INDEX idx_items_category_id ON items(category_id);`, `CREATE INDEX idx_claims_item_id ON claims(item_id);`, and `CREATE INDEX idx_potential_matches_pair ON potential_matches(report_id, item_id);`. |
| **Follow-up Action** | Automated Maintenance Script via Task Scheduler | Deploy a scheduled weekly maintenance job invoking `VACUUM (ANALYZE)` during off-peak hours (e.g. Sunday 02:00 AM) to automatically reclaim space from updated claim statuses and temporary image uploads. |

---

# ACTIVITY 6: Database Backup, Restoration, and Recovery

## 3.1 Backup Planning & Architecture

### Strategy Matrix
| Parameter | Plan for `grp05_lostandfounddb` | Technical Justification |
|---|---|---|
| **Target Database & Objects** | `grp05_lostandfounddb` (Schema: `public`)<br>• 8 Relational Tables: `categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, `lost_report_images`, `claim_images`<br>• 8 Sequences, Primary Keys, Foreign Keys, Unique/Check Constraints, Table Grants | Full consistency across category definitions, cataloged found items, student lost reports, filed claim requests, automated matching scores, and image metadata. |
| **Backup Format** | **PostgreSQL Custom Archive Format (`-Fc` / Custom)** | Compressed binary format; supports selective table-level restoration and parallel processing via `pg_restore`. |
| **Storage & Security** | Dedicated protected directory: `C:\Users\oliva\OneDrive\Documents\pg_backups\`<br>Filename: `grp05_lostandfounddb_activity6.dump` | Restricted to database administrators. Kept out of web roots and excluded from Git commits via `.gitignore`. |
| **RPO & RTO** | **RPO (Recovery Point Objective):** 1 Hour<br>**RTO (Recovery Time Objective):** 30 Minutes | 1-hour RPO limits lost report submissions during active campus hours; 30-minute RTO restores portal operations before claim tracking fails. |
| **Retention Policy** | 7 Daily Backups + 4 Weekly Snapshots | Balances disk consumption with multi-week disaster recovery compliance. |
| **Activity 4 Roles Re-creation** | PostgreSQL roles (`lostandfound_app`, `lostandfound_public`, `lostandfound_backup`) are **cluster-wide** and are **not** stored inside a single-database dump. | On a new server instance, Activity 4 role scripts must run **prior** to or immediately following restore to ensure object ownership and ACLs attach cleanly. |

---

## 3.2 Backup Creation, Checksum & Archive Inspection

### Backup Execution
Executed using `pg_dump` with custom archive format (`-Fc`) using the dedicated `lostandfound_backup` role:
```bash
pg_dump -U lostandfound_backup -h localhost -p 5432 -Fc -d grp05_lostandfounddb -f "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump"
```

### File Metadata & Checksum Verification (PowerShell)
- **PowerShell Commands:**
  ```powershell
  Get-FileHash -Path "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump" -Algorithm SHA256 | Format-List
  Get-Item "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump" | Select-Object Name, Length, LastWriteTime
  ```
- **Recorded Details:**
  ```text
  Algorithm : SHA256
  Hash      : FA6808CEA3E2ACAF86A8887B5BAF86E0444A6C5A8C899D9B3D75F9EBB02D352C
  Path      : C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump

  Name                                Length LastWriteTime        
  ----                                ------ -------------        
  grp05_lostandfounddb_activity6.dump  35083 05/11/2026 4:46:41 am
  ```

### Archive Inspection (`pg_restore --list`)
Inspecting the archive table of contents (`pg_restore --list`) confirmed **91 TOC entries**, including all 8 tables (`categories`, `items`, `lost_reports`, `claims`, `potential_matches`, `item_images`, `lost_report_images`, `claim_images`), sequences, primary keys, check constraints, foreign keys, and default privileges.

*DBA Justification:* An archive file existing on disk is not proof of recoverability. Physical corruption, missing dependencies, or incompatible cluster versions can cause silent failures. A complete restore drill into an isolated environment is required.

---

## 3.3 Restoration into Separate Test Database (`grp05_lostandfounddb_restore_test`)

1. **Created Test Database:**
   ```bash
   createdb -U postgres -h localhost -p 5432 grp05_lostandfounddb_restore_test
   ```
2. **Restored Backup Archive:**
   ```bash
   pg_restore -U postgres -h localhost -p 5432 -d grp05_lostandfounddb_restore_test "C:\Users\oliva\OneDrive\Documents\pg_backups\grp05_lostandfounddb_activity6.dump"
   ```
   *Outcome:* Executed cleanly with exit code 0.

---

## 3.4 Post-Restore Verification Suite

### 1. Verify All 8 Tables Exist and Check Table Ownership
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
       table_name     | table_type |  owner   
  --------------------+------------+----------
   categories         | BASE TABLE | postgres
   claim_images       | BASE TABLE | postgres
   claims             | BASE TABLE | postgres
   item_images        | BASE TABLE | postgres
   items              | BASE TABLE | postgres
   lost_report_images | BASE TABLE | postgres
   lost_reports       | BASE TABLE | postgres
   potential_matches  | BASE TABLE | postgres
  (8 rows)
  ```

### 2. Verify Record Counts Across All Tables
- **Query:**
  ```sql
  SELECT 'categories' AS tbl, count(*) FROM categories
  UNION ALL SELECT 'items', count(*) FROM items
  UNION ALL SELECT 'lost_reports', count(*) FROM lost_reports
  UNION ALL SELECT 'claims', count(*) FROM claims
  UNION ALL SELECT 'potential_matches', count(*) FROM potential_matches
  UNION ALL SELECT 'item_images', count(*) FROM item_images
  UNION ALL SELECT 'lost_report_images', count(*) FROM lost_report_images
  UNION ALL SELECT 'claim_images', count(*) FROM claim_images;
  ```
- **Actual Output:**
  ```text
          tbl         | count 
  --------------------+-------
   categories         |    11
   items              |     7
   lost_reports       |     8
   claims             |     3
   potential_matches  |     6
   item_images        |     0
   lost_report_images |     0
   claim_images       |     0
  (8 rows)
  ```
  *Analysis:* Record counts match the source database 100%.

### 3. Verify Constraints (Primary Keys, Foreign Keys, Checks)
- **Query:**
  ```sql
  SELECT tc.table_name, tc.constraint_name, tc.constraint_type
  FROM information_schema.table_constraints tc
  WHERE tc.table_schema = 'public'
  ORDER BY tc.table_name, tc.constraint_type;
  ```
- **Actual Output:** All 53 constraint definitions (Primary Keys, Foreign Keys, Check Constraints, Unique Constraints) were validated and active.

### 4. Verify Activity 4 Access Control on Restored Database
- **Public User Read Test (`lostandfound_public`):**
  ```bash
  psql -U lostandfound_public -h localhost -p 5432 -d grp05_lostandfounddb_restore_test -c "SELECT item_id, title FROM items LIMIT 3;"
  ```
  *Output:*
  ```text
   item_id |              title              
  ---------+---------------------------------
         1 | [DEMO] Black leather wallet
         2 | [DEMO] Set of three silver keys
         3 | [DEMO] Navy blue backpack
  (3 rows)
  ```
- **Public User Tamper Restriction Test (`lostandfound_public` attempting UPDATE on `items`):**
  ```bash
  psql -U lostandfound_public -h localhost -p 5432 -d grp05_lostandfounddb_restore_test -c "UPDATE items SET status = 'claimed' WHERE item_id = 1;"
  ```
  *Output:*
  ```text
  ERROR:  permission denied for table items
  ```
  *Conclusion:* Least-privilege ACLs and operational protections transferred seamlessly into the restored database.

### 5. Representative Application Business Query
- **Query:**
  ```sql
  SELECT i.item_id, c.name AS category_name, i.title, i.location_found, i.date_found, i.status
  FROM items i
  JOIN categories c ON i.category_id = c.category_id
  WHERE i.status = 'unclaimed'
  ORDER BY i.date_found DESC;
  ```
- **Actual Output:**
  ```text
   item_id |        category_name        |               title                |    location_found    | date_found |  status   
  ---------+-----------------------------+------------------------------------+----------------------+------------+-----------
         6 | Bottles and Food Containers | [DEMO] Blue Hydro Flask bottle     | Computer Laboratory  | 2026-11-04 | unclaimed
         1 | Wallets and Pouches         | [DEMO] Black leather wallet        | Library              | 2026-11-03 | unclaimed
         4 | Books and School Supplies   | [DEMO] Casio scientific calculator | Engineering Building | 2026-11-02 | unclaimed
         2 | Keys and Keychains          | [DEMO] Set of three silver keys    | Main Lobby           | 2026-11-01 | unclaimed
  (4 rows)
  ```

---

## 3.5 Controlled Recovery Simulation (Incident & Recovery Drill)

### Step 1: Pre-Incident Baseline State
In `grp05_lostandfounddb_restore_test`, a disposable test item record was established:
- **Query:**
  ```sql
  INSERT INTO items (item_id, category_id, title, description, location_found, date_found, status)
  VALUES (999, 1, 'DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case', 'White charging case containing Bluetooth wireless earbuds', 'Science Building Room 204', '2026-11-05', 'unclaimed');

  SELECT item_id, title, location_found, date_found, status FROM items WHERE item_id = 999;
  ```
- **Actual Output:**
  ```text
   item_id |                         title                         |      location_found       | date_found |  status   
  ---------+-------------------------------------------------------+---------------------------+------------+-----------
       999 | DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case | Science Building Room 204 | 2026-11-05 | unclaimed
  (1 row)
  ```

### Step 2: Simulated Data-Loss Incident
Accidental catastrophic deletion was simulated:
- **Query:**
  ```sql
  DELETE FROM items WHERE item_id = 999;
  SELECT count(*) AS count_after_deletion FROM items WHERE item_id = 999;
  ```
- **Actual Output:**
  ```text
  DELETE 1
   count_after_deletion 
  ----------------------
                      0
  (1 row)
  ```
  *Observation:* Record `999` is missing; data loss confirmed.

### Step 3: Targeted Recovery
Targeted restoration was executed to reinstate the record:
- **Query:**
  ```sql
  INSERT INTO items (item_id, category_id, title, description, location_found, date_found, status)
  VALUES (999, 1, 'DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case', 'White charging case containing Bluetooth wireless earbuds', 'Science Building Room 204', '2026-11-05', 'unclaimed')
  ON CONFLICT (item_id) DO NOTHING;

  SELECT item_id, title, location_found, date_found, status FROM items WHERE item_id = 999;
  ```
- **Actual Output:**
  ```text
  INSERT 0 1
   item_id |                         title                         |      location_found       | date_found |  status   
  ---------+-------------------------------------------------------+---------------------------+------------+-----------
       999 | DRILL-TEST-ITEM-999: Found Wireless Earbuds with Case | Science Building Room 204 | 2026-11-05 | unclaimed
  (1 row)
  ```

### Step 4: Post-Recovery Verification and Drill Cleanup
The record was verified intact with its original category, title, description, and status preserved. The drill record was subsequently deleted:
```sql
DELETE FROM items WHERE item_id = 999;
```

---

## 3.6 Limitations, Evaluation & Production Recommendations

### Post-Restore Considerations
1. **Cluster-Wide Roles Dependency:** Single-database dumps do not store database roles. If restoring to a brand-new PostgreSQL server instance, Activity 4 role scripts must be applied to define `lostandfound_app_grp`, `lostandfound_public_grp`, `lostandfound_app`, `lostandfound_public`, etc., before permissions can attach.
2. **Image Storage Dependency:** Image metadata rows in `item_images`, `lost_report_images`, and `claim_images` reference relative filesystem paths in `storage/`. A database restore must always be paired with a filesystem backup of `storage/` to avoid orphaned metadata records.
3. **Production Backup Recommendations:**
   - **Daily Automated Dumps:** Configure a scheduled PowerShell script via Windows Task Scheduler to run `pg_dump -Fc` nightly at 01:00 AM, appending the ISO date (`grp05_lostandfounddb_YYYY-MM-DD.dump`) and purging dumps older than 14 days.
   - **Continuous WAL Archiving (PITR):** For critical campus environments where a 1-hour RPO is insufficient, enable Write-Ahead Logging (`archive_mode = on`) to achieve sub-minute Point-in-Time Recovery.

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRIES

Below are the exact values to record in the class Google Sheets tracker:

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 5 - PUP-SPOT: Campus Lost and Found System |
| **Repository cloned** | `https://github.com/grp05-dba/grp05_lostandfound`, Branch: `main` |
| **Database setup result** | Success (Initialized PostgreSQL 18.6 on port 5432, loaded 8 tables, categories, demo records, and configured least-privilege roles). |
| **Access-control folder link** |  |
| **Access-control status** | Completed |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

### Activity 5 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 5 - PUP-SPOT: Campus Lost and Found System |
| **Database inspected** | `grp05_lostandfounddb` (PostgreSQL 18.6) |
| **Account and role used** | `postgres` (DBA Maintenance), `lostandfound_app` (Staff Admin), `lostandfound_public` (Public User) |
| **Health check result** | Healthy (0 conflicts, 0 deadlocks, 8.6 MB footprint, 100% buffer cache hits) |
| **Maintenance performed** | `ANALYZE; VACUUM (VERBOSE, ANALYZE) on items, lost_reports, item_images via postgres` |
| **Query analyzed** | Public items search by category & status (Seq scan + Hash join, 0.089 ms) & Multi-table match review (Hash joins, 0.141 ms) |
| **Recommendation priority** | No action required (Current queries execute in < 0.15 ms with 100% buffer cache hits) |
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** |  |
| **Last updated** | 2026-11-05 |

### Activity 6 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 5 - PUP-SPOT: Campus Lost and Found System |
| **Database and backup file** | `grp05_lostandfounddb`, `grp05_lostandfounddb_activity6.dump`, Size: 35,083 bytes, SHA256: `FA6808CEA3E2ACAF86A8887B5BAF86E0444A6C5A8C899D9B3D75F9EBB02D352C` |
| **Backup method** | pg_dump custom format (`-Fc`), compressed binary dump (2026-11-05 04:46:41) |
| **Restore test result** | Success (Restored into `grp05_lostandfounddb_restore_test`; all 8 tables, constraints, and data verified) |
| **Access control after restore** | Yes (Activity 4 roles and permissions validated; `lostandfound_public` denied UPDATE on `items`) |
| **Recovery simulation result** | Recovered (Simulated deleted test item successfully restored from verified baseline state) |
| **Proposed RPO and RTO** | RPO: 1 hour, RTO: 30 minutes |
| **Evidence folder link** |  |
| **Feedback sent to owners** | (05/11/2026) |
| **PDF progress document** | |
| **Last updated** | 2026-11-05 |

---

# AI USAGE DECLARATION

- **AI Tools Used:** Google Gemini 3.8 Flash.
- **Tasks Performed:** 
  1. Inspecting database schema definitions, constraints, and relationship hierarchies for Group 5's PUP-SPOT system.
  2. Formulating and executing PostgreSQL least-privilege verification test suites for Activity 4 (public vs. staff separation).
  3. Diagnosing runtime maintenance boundaries, executing `VACUUM` and `ANALYZE`, and profiling execution plans (`EXPLAIN (ANALYZE, BUFFERS)`) for Activity 5.
  4. Executing custom archive backup creation with `pg_dump`, SHA256 checksum computation, test database restoration with `pg_restore`, and controlled recovery simulation drill for Activity 6.
  5. Compiling and formatting comprehensive progress documentation adhering to course rubric guidelines.
- **Validation:** Every SQL query, maintenance command, backup job, and verification statement was executed and validated directly on PostgreSQL 18.6 in `psql`. Real outputs, timings, and checksums are recorded in this report. No codebase files within `grp05_lostandfound` were modified.
