# COMP 018: Database Administration - Activity 4: Access Control Report
## Group Evaluation Report: Group 08 - Campus Canteen Pre-Order and Queue Management System

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

# ACTIVITY 4: Database Users, Roles, Privileges, and Access Control

## 1.1 Access-Control Plan & Architecture

### Problem in Activity 3
In Activity 3, the Spring Boot application connected directly using `canteen_admin`, the database and table owner. This setup violated the **Principle of Least Privilege** because the running web application had full DDL and unconstrained DML capabilities (could drop tables, alter schemas, or purge historical records).

### Activity 4 Solution
We designed and implemented a role-based access control (RBAC) model decoupling administrative database ownership from daily operational traffic:
- **Group Roles (`NOLOGIN`):** Common privilege bundles representing distinct responsibility profiles (`canteen_app_grp` and `canteen_readonly_grp`).
- **Login Roles (`LOGIN`):** Individual authenticated database accounts assigned to their corresponding group roles (`canteen_app` and `canteen_report_user`).
- **Least Privilege Enforcement:** The runtime account (`canteen_app`) is granted `SELECT`, `INSERT`, and `UPDATE` on public tables, but its `DELETE` privilege is strictly scoped to `menu_item`. It cannot delete orders, order items, students, queue entries, or notifications.

### Access-Control Matrix
| Login Account | Group Role (`NOLOGIN`) | Application Role | Responsibilities | Assigned Privileges | Technical Justification |
|---|---|---|---|---|---|
| `canteen_app` | `canteen_app_grp` | Application Runtime | Handles all live student, vendor, order, and queue API traffic | • `CONNECT` on `canteen_db`<br>• `USAGE` on schema `public`<br>• `SELECT, INSERT, UPDATE` on all 7 tables<br>• `DELETE` **strictly on `menu_item`**<br>• `USAGE` on all sequences | Restricting `DELETE` strictly to `menu_item` ensures that even in the event of an SQL injection or API flaw, order histories, student accounts, queue tokens, and notification logs cannot be erased. |
| `canteen_report_user` | `canteen_readonly_grp` | Auditor / Management | Generates sales analytics, queue inspections, and audit reports | • `CONNECT` on `canteen_db`<br>• `USAGE` on schema `public`<br>• `SELECT` on all 7 tables | Pure read-only inspection. Cannot insert fake orders, manipulate prices, or modify data. |
| `canteen_admin` | *(None / Table Owner)* | Database Administrator | Schema migrations, DDL, table ownership, and maintenance | Full owner privileges on `canteen_db` and all 7 tables | Used exclusively for setup, maintenance, and migrations; never used by the live Spring Boot API. |

---

## 1.2 SQL Scripts Summary & Setup Execution

The following script was executed as `postgres` to construct the access-control hierarchy:

```sql
-- 1. Create Group Roles (NOLOGIN)
CREATE ROLE canteen_app_grp NOLOGIN;
CREATE ROLE canteen_readonly_grp NOLOGIN;

-- 2. Create Login Accounts (LOGIN)
CREATE ROLE canteen_app WITH LOGIN PASSWORD 'AppRuntimePass2026!';
CREATE ROLE canteen_report_user WITH LOGIN PASSWORD 'ReportUserPass2026!';

-- 3. Assign Group Memberships
GRANT canteen_app_grp TO canteen_app;
GRANT canteen_readonly_grp TO canteen_report_user;

-- 4. Harden Default Privileges (Revoke broad PUBLIC access)
REVOKE ALL ON DATABASE canteen_db FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- 5. Grant Database & Schema Access
GRANT CONNECT ON DATABASE canteen_db TO canteen_app_grp, canteen_readonly_grp;
GRANT USAGE ON SCHEMA public TO canteen_app_grp, canteen_readonly_grp;

-- 6. Grant Object-Level Privileges to Application Runtime Group
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO canteen_app_grp;
GRANT DELETE ON menu_item TO canteen_app_grp;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO canteen_app_grp;

-- 7. Grant Read-Only Privileges to Auditor Group
GRANT SELECT ON ALL TABLES IN SCHEMA public TO canteen_readonly_grp;
```

---

## 1.3 Verification Results: Test Suite 1 (Runtime User `canteen_app`)

### Test 1.1: Read Data (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  SELECT vendor_id, vendor_name, contact_number, is_active FROM vendor;
  RESET ROLE;
  ```
- **Expected Result:** Allowed; returns list of vendors.
- **Actual Result / Output:**
  ```text
  vendor_id | vendor_name         | contact_number | is_active
  ----------+---------------------+----------------+----------
          1 | Aling Nena's Silog  | 09171234567    | true
          2 | Kuya Jun's Burgers  | 09181234567    | true
          3 | Merienda Corner     | 09191234567    | true
          4 | Waffle Corner       | 09201234567    | false
          5 | Stall 161508        | [null]         | true
  (5 rows)
  Query returned successfully in 52 msec.
  ```

### Test 1.2: Insert an Item / Advance Sequence (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  INSERT INTO menu_item (vendor_id, item_name, price, is_available)
  VALUES (1, 'Activity 4 Special Siomai', 35.00, true);
  RESET ROLE;
  ```
- **Expected Result:** Allowed; inserts record and consumes sequence value.
- **Actual Result / Output:**
  ```text
  INSERT 0 1
  Query returned successfully in 84 msec.
  ```

### Test 1.3: Delete a Menu Item (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  DELETE FROM menu_item WHERE item_name = 'Activity 4 Special Siomai';
  RESET ROLE;
  ```
- **Expected Result:** Allowed; runtime account is authorized to remove unreferenced menu items.
- **Actual Result / Output:**
  ```text
  DELETE 1
  Query returned successfully in 81 msec.
  ```

### Test 1.4: Delete Customer Order Records (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  DELETE FROM customer_order WHERE order_id = 1;
  RESET ROLE;
  ```
- **Expected Result:** Denied; application runtime account must not delete customer transactions.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table customer_order
  SQL state: 42501
  ```

### Test 1.5: Alter Table / DDL Operations (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  DROP TABLE notification;
  RESET ROLE;
  ```
- **Expected Result:** Denied; DDL requires table ownership.
- **Actual Result / Output:**
  ```text
  ERROR: must be owner of table notification
  SQL state: 42501
  ```

---

## 1.4 Verification Results: Test Suite 2 (Read-Only / Auditor User `canteen_report_user`)

### Test 2.1: Read Sales and Orders Report (Allowed)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  SELECT co.order_id, s.full_name AS student_name, v.vendor_name, co.status, q.queue_number
  FROM customer_order co
  JOIN student s ON co.student_id = s.student_id
  JOIN vendor v ON co.vendor_id = v.vendor_id
  LEFT JOIN queue_entry q ON co.order_id = q.order_id;
  RESET ROLE;
  ```
- **Expected Result:** Allowed; returns joined multi-table report.
- **Actual Result / Output:**
  ```text
  order_id | student_name   | vendor_name        | status     | queue_number
  ---------+----------------+--------------------+------------+-------------
         1 | Maria Santos   | Aling Nena's Silog | cancelled  |            1
         2 | Juan Dela Cruz | Aling Nena's Silog | preparing  |            2
         3 | Student 161508 | Stall 161508       | completed  |            1
         4 | Student 161508 | Stall 161508       | cancelled  |            2
  (4 rows)
  Query returned successfully in 68 msec.
  ```

### Test 2.2: Attempt to Insert Data (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  INSERT INTO vendor (vendor_name, contact_number, is_active)
  VALUES ('Unauthorized Stall', '09999999999', true);
  RESET ROLE;
  ```
- **Expected Result:** Denied; read-only account cannot insert data.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table vendor
  SQL state: 42501
  ```

### Test 2.3: Attempt to Modify Existing Records (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  UPDATE student SET role = 'ADMIN' WHERE student_id = 1;
  RESET ROLE;
  ```
- **Expected Result:** Denied; read-only account cannot modify data or elevate user privileges.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table student
  SQL state: 42501
  ```

### Test 2.4: Attempt to Delete Records (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_report_user;
  DELETE FROM menu_item WHERE item_id = 1;
  RESET ROLE;
  ```
- **Expected Result:** Denied; read-only account cannot delete data.
- **Actual Result / Output:**
  ```text
  ERROR: permission denied for table menu_item
  SQL state: 42501
  ```

---

## 1.5 Verification Results: Test Suite 3 (Cross-Database & Administrative Boundary)

### Test 3.1: Attempt to Create a Database (Denied)
- **SQL Statement:**
  ```sql
  SET ROLE canteen_app;
  CREATE DATABASE rogue_test_db;
  RESET ROLE;
  ```
- **Expected Result:** Denied; application users must not possess administrative cluster privileges (`CREATEDB`).
- **Actual Result / Output:**
  ```text
  ERROR: permission denied to create database
  SQL state: 42501
  ```

---

## 1.6 Issues, Fixes, and Feedback to Owners

### Issues and Fixes Encountered
1. **Port 8080 Collision:** During local deployment, port 8080 was bound by `PEMHTTPD-x64` (PostgreSQL Enterprise Manager Apache service). Rather than breaking local services, we configured `SERVER_PORT=8081` in the backend environment and updated `VITE_API_URL` in the frontend client.
2. **Missing Least Privilege in Activity 3:** The original application documentation relied on `canteen_admin` for API runtime traffic. We decoupled this by creating `canteen_app_grp` and `canteen_app`, scoping runtime permissions strictly to necessary operations.

### Feedback to Owning Group (Group 8: bwisIT Solutions)
- **Commendation:** The migration files (`001_create_tables.sql`, `002_auth_columns.sql`) and sample data (`001_sample_data.sql`) ran cleanly and idempotently with foreign key constraints and PostgreSQL transaction advisory locks.
- **Recommendation 1:** Configure `application.properties` with `server.port=${SERVER_PORT:8080}` to allow dynamic port binding without code edits.
- **Recommendation 2:** Adopt `canteen_app` (member of `canteen_app_grp`) as the standard `DB_USER` in `.env.example` instead of `canteen_admin` to enforce least privilege out-of-the-box.

---

---

# CONSOLIDATED CLASS GOOGLE SHEETS TRACKER ENTRY

### Activity 4 Log Tab
| Column Header | Value |
|---|---|
| **Project** | Group 8 - bwisIT Solutions: Campus Canteen Pre-Order and Queue Management System |
| **Repository cloned** | `https://github.com/EclipseChan/bwisIT-Solutions---Campus-Canteen-Pre-Order-and-Queue-Management-System`, Branch: `main` |
| **Database setup result** | Success (Port 8080 collision with local PEMHTTPD identified; resolved by port remapping to 8081). All 7 tables and seed data verified. |
| **Access-control folder link** | `https://github.com/Nycri/brewpos/tree/main/access-control` |
| **Access-control status** | Completed |
| **Feedback sent to owners** | Yes (04/11/2026) |
| **PDF progress document** | *(Link to your group's shared PDF document)* |
| **Last updated** | 2026-11-04 |

---
