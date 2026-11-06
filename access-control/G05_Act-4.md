# COMP 018: Database Administration - Activity 4: Access Control Report
## Group Evaluation Report: Group 05 - PUP-SPOT: Campus Lost and Found System

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

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 5 - PUP-SPOT: Campus Lost and Found System |
| **Repository cloned** | `https://github.com/grp05-dba/grp05_lostandfound`, Branch: `main` |
| **Database setup result** | Success (Initialized PostgreSQL 18.6 on port 5432, loaded 8 tables, categories, demo records, and configured least-privilege roles). |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/access-control` |
| **Access-control status** | Completed |
| **Feedback sent to owners** | Yes (05/11/2026) |
| **PDF progress document** | *(Link to your group's shared PDF document)* |
| **Last updated** | 2026-11-05 |

---
