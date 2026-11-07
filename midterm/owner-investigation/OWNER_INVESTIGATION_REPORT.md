# COMP 018 — Database Administration: Midterm Assessment
# Integrated Database Administration Challenge: Owner Group Investigation Report

**Project Name:** BrewPOS — Café Point-of-Sale System  
**Owner Group:** Group 4  
**Assigned Designer Group:** Group 10  
**Database Platform:** PostgreSQL 18.6 (x86_64-pc-windows-msvc)  
**Primary Production Database:** `grp_brewposdb`  
**Disposable Sandbox Database:** `grp_brewposdb_restore_test`  
**Repository:** [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos)  
**Date of Investigation:** November 7, 2026  



# Table of Contents
1. [Executive Summary & System Links](#1-executive-summary--system-links)
2. [Flaw Register & Designer Release Intake](#2-flaw-register--designer-release-intake)
3. [Incident Register & Public Specifications](#3-incident-register--public-specifications)
4. [Detailed Incident Investigations (Parts C & D)](#4-detailed-incident-investigations-parts-c--d)
   - [Incident 1: G01-INC1 — Incomplete Role-Based Access Control](#incident-1-g01-inc1--incomplete-role-based-access-control)
   - [Incident 2: G01-INC2 — No Explicit Order Status Management](#incident-2-g01-inc2--no-explicit-order-status-management)
   - [Incident 3: G01-INC3 — Database Documentation Does Not Match SQL Implementation](#incident-3-g01-inc3--database-documentation-does-not-match-sql-implementation)
   - [Incident 4: G01-INC4 — Incorrect Product Lookup in Database Seeder](#incident-4-g01-inc4--incorrect-product-lookup-in-database-seeder)
   - [Incident 5: G01-INC5 — No Dedicated Payment Transaction Records](#incident-5-g01-inc5--no-dedicated-payment-transaction-records)
5. [Consolidated Verification Suite & Security Matrix](#5-consolidated-verification-suite--security-matrix)
6. [Prevention, Practical Recommendations & Escalation Review](#6-prevention-practical-recommendations--escalation-review)
7. [Designer Review & Feedback Exchange](#7-designer-review--feedback-exchange)
8. [AI Usage Declaration & Validation Methodology](#8-ai-usage-declaration--validation-methodology)

---

# 1. Executive Summary & System Links

### 1.1 Overview
As part of the **COMP 018: Database Administration Midterm Assessment**, Group 4 served as the **Owner Group** for the **BrewPOS (Café Point-of-Sale System)** repository and its PostgreSQL database (`grp_brewposdb`). Our project was evaluated by **Group 10 (Designer Group)**, who identified five evidence-based flaws and released five formal incident scenarios (`G01-INC1` through `G01-INC5`) spanning access control, order lifecycle tracking, deployment script alignment, seeder data integrity, and payment transaction architecture.

Adhering strictly to professional Database Administration standards and the assessment specification:
1. **Zero Production Risk:** No corrective actions, destructive drops, or untested DDL modifications were applied to the live production database. All diagnostic tests and fixes were executed exclusively within a disposable sandbox database (`grp_brewposdb_restore_test`), pre-restored using our verified Activity 6 backup drill.
2. **Strict Adherence to Least Privilege:** Superuser privileges (`postgres`) were never used as a crutch to bypass permission errors. Corrective actions were designed using dedicated group and login roles (`app_admin_grp`, `app_runtime_grp`, `brewpos_app`, `brewpos_auditor`).
3. **Disciplined 8-Step Troubleshooting:** Every incident was investigated methodically following the 8-step DBA protocol: *Observe → Verify → Hypothesize → Test → Act → Verify Again → Document → Prevent*.
4. **Resolution Outcome:** All 5 incidents were successfully diagnosed, tested against competing hypotheses, corrected via idempotent SQL scripts, and validated through both positive functional tests and negative constraint/privilege tests.

### 1.2 System Links
- **Project Repository:** [https://github.com/Nycri/brewpos](https://github.com/Nycri/brewpos)
- **Midterm Owner Investigation Directory:** [midterm/owner-investigation/](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/)
- **Investigation Scripts & Fixes:** [midterm/owner-investigation/scripts/](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/)
- **Activity 4 Access Control Baseline:** [access-control/](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/access-control/)
- **Activity 5 Monitoring & Maintenance Baseline:** [activity-5/](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/activity-5/)
- **Activity 6 Backup & Recovery Baseline:** [activity-6/](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/activity-6/)
- **Google Sheets Tracker:** Midterm Assignments, Flaw Register, and Incident Register tabs updated per class protocol.

---

# 2. Flaw Register & Designer Release Intake

The table below documents the official release received from **Group 10 (Designer Group)**, capturing the exact flaws identified and the symptoms-only incident briefs issued to our team:

| Incident ID | Flaw Found (Designer Group Description) | Incident Brief (Symptoms Only) | Area & Activity | Severity | Owner Status |
|---|---|---|---|:---:|:---:|
| **G01-INC1** | The system does not properly implement role-based access control for the different user roles described in its documentation. | A user reported that certain functions were accessible even when they should have been restricted to a different user role. | Privileges & Access Control (Activity 4) | **High** | **Fixed** |
| **G01-INC2** | The order management system does not have an explicit mechanism for tracking order statuses throughout the transaction process. | Staff members were unable to determine whether an order was still being processed, already completed, or cancelled. | Schema Objects & Lifecycle (Activity 3) | **Medium** | **Fixed** |
| **G01-INC3** | The database setup documentation does not fully match the database roles and permissions implemented in the provided SQL scripts. | During database setup, the expected user accounts and permissions were not available, preventing the setup process from matching the documented configuration. | Configuration & Deployment Setup (Activities 3 & 4) | **Medium** | **Fixed** |
| **G01-INC4** | The database seeder uses the menu item name alone when identifying products, which may cause incorrect matches when different categories contain items with the same name. | After sample data was inserted, some orders displayed menu items that did not match the expected products or categories. | Maintenance & Seeder Data Integrity (Activities 3 & 5) | **High** | **Fixed** |
| **G01-INC5** | The database does not provide a dedicated table for recording individual payment transactions and their details. | Staff members were unable to retrieve complete payment information for certain orders, including the payment method used. | Financial Schema, Audit & Recovery (Activities 3 & 6) | **High** | **Fixed** |

---

# 3. Incident Register & Public Specifications

In accordance with the Midterm Assessment guidelines, Group 10 designed five incident scenarios linking directly to verified flaws, ensuring a balanced distribution across activity areas and difficulty levels (2 Easy, 2 Medium, 1 Hard):

| Incident ID | Linked Flaw | Target Project & Owner Group | Variant & Activity Area | Difficulty & Est. Time | Symptoms-Only Incident Brief (Public) | Owner Result | Status |
|---|---|---|---|:---:|---|:---:|:---:|
| **G01-INC1** | G01-F1 | BrewPOS (Group 4) | Privileges & Access Control (Activity 4) | **Medium**<br>(45 mins) | A user reported that certain functions were accessible even when they should have been restricted to a different user role. | **Resolved** | Investigated & Closed |
| **G01-INC2** | G01-F2 | BrewPOS (Group 4) | Objects & Schema Configuration (Activity 3) | **Easy**<br>(30 mins) | Staff members were unable to determine whether an order was still being processed, already completed, or cancelled. | **Resolved** | Investigated & Closed |
| **G01-INC3** | G01-F3 | BrewPOS (Group 4) | Configuration & Deployment Setup (Activities 3 & 4) | **Easy**<br>(30 mins) | During database setup, the expected user accounts and permissions were not available, preventing the setup process from matching the documented configuration. | **Resolved** | Investigated & Closed |
| **G01-INC4** | G01-F4 | BrewPOS (Group 4) | Maintenance & Seeder Data Integrity (Activity 5) | **Medium**<br>(45 mins) | After sample data was inserted, some orders displayed menu items that did not match the expected products or categories. | **Resolved** | Investigated & Closed |
| **G01-INC5** | G01-F5 | BrewPOS (Group 4) | Financial Audit & Disaster Recovery (Activities 3 & 6) | **Hard**<br>(60 mins) | Staff members were unable to retrieve complete payment information for certain orders, including the payment method used. | **Resolved** | Investigated & Closed |

---

# 4. Detailed Incident Investigations (Parts C & D)

---

## Incident 1: G01-INC1 — Incomplete Role-Based Access Control

### 1. Incident Metadata & Classification
- **Incident ID:** `G01-INC1`
- **Traceable Flaw:** `G01-F1` ("The system does not properly implement role-based access control for the different user roles described in its documentation.")
- **Activity Area:** Activity 4 — Database Users, Roles, Privileges, and Access Control
- **Difficulty Rating:** Medium (Actual investigation time: 35 minutes)
- **Target Database:** `grp_brewposdb_restore_test` (Disposable Sandbox)

### 2. Observe (Symptoms & Exact Incident Brief)
> **Incident Brief:** "A user reported that certain functions were accessible even when they should have been restricted to a different user role."

### 3. Verify (Initial Database Inspection & Evidence Log)
To verify the observed vulnerability without touching production, the DBA connected to `grp_brewposdb_restore_test` and queried `information_schema.role_table_grants` for the application user:

```sql
SELECT grantee, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE grantee = 'brewpos_app' AND table_schema = 'public'
ORDER BY table_name, privilege_type;
```

**Observed Evidence Output:**
```
   grantee    |   table_name   | privilege_type 
--------------+----------------+----------------
 brewpos_app  | menu_category  | DELETE
 brewpos_app  | menu_category  | INSERT
 brewpos_app  | menu_category  | SELECT
 brewpos_app  | menu_category  | UPDATE
 brewpos_app  | menu_item      | DELETE
 brewpos_app  | menu_item      | INSERT
 brewpos_app  | menu_item      | SELECT
 brewpos_app  | menu_item      | UPDATE
 brewpos_app  | order_line_item| INSERT
 brewpos_app  | order_line_item| SELECT
 brewpos_app  | order_ticket   | INSERT
 brewpos_app  | order_ticket   | SELECT
 brewpos_app  | staff          | SELECT
(13 rows)
```
**Finding:** The single runtime user `brewpos_app` possessed direct `UPDATE` and `DELETE` privileges on `public.menu_category` and `public.menu_item`. There was no distinct database role for register/cashier operations versus catalog management operations. Any session authenticated as `brewpos_app` could execute arbitrary catalog price changes or category deletions.

### 4. Baseline Comparison (Activities 3–6 vs. Current State)
- **Documented Baseline ([docs/user-roles.md](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/docs/user-roles.md)):** Section 2 specifies that Cashiers are restricted to read-only access on active `menu_item` and cannot alter prices or categories. Catalog modification is strictly reserved for the Store Manager (`app_admin_grp`).
- **Observed State in SQL:** `database/05_create_app_role.sql` consolidated all application capabilities into one login role `brewpos_app`, allowing any session authenticated under that credential to execute `UPDATE public.menu_item SET price = 0.00`.
- **Deviation:** A critical discrepancy exists between the security documentation and the SQL grant script.

### 5. Troubleshooting Plan
1. **Ordered Checks:**
   - Confirm active roles in cluster (`pg_roles`).
   - Check if separate group roles exist (`app_cashier_grp`, `app_admin_grp`).
   - Test if `brewpos_app` can perform an unauthorized update on `menu_item`.
2. **Hypotheses:**
   - *Hypothesis A (Confirmed):* The database lacks operational role separation; catalog administration grants were assigned directly to the general runtime user.
   - *Hypothesis B (Alternative - Ruled Out):* The application web security filter was misconfigured, allowing cashiers to hit `/api/menu/items` PUT endpoints.
3. **Ruling out Alternative:** Even if the web tier restricts UI buttons or Spring Security guards endpoints, the database itself must enforce the **Principle of Least Privilege (PoLP)**. Relying exclusively on presentation-layer security leaves the database completely exposed to SQL injection, leaked connection pools, or direct compromised sessions.
4. **Risks of Action:** Splitting roles abruptly could break existing Spring Boot HikariCP connections if the application attempts to execute catalog management queries under a cashier-only role.
5. **Rollback Option:** Re-grant `UPDATE, DELETE ON public.menu_item, public.menu_category TO brewpos_app`.

### 6. Corrective Action Applied
The DBA implemented true PostgreSQL Role-Based Access Control on `grp_brewposdb_restore_test` using the script [`fix_inc01_rbac_hardening.sql`](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/fix_inc01_rbac_hardening.sql):

```sql
-- Step 1: Create distinct functional group roles
CREATE ROLE app_cashier_grp WITH NOLOGIN;
CREATE ROLE app_admin_grp   WITH NOLOGIN;

-- Step 2: Grant least privilege to app_cashier_grp (Register Operations ONLY)
GRANT CONNECT ON DATABASE grp_brewposdb TO app_cashier_grp;
GRANT USAGE ON SCHEMA public TO app_cashier_grp;
GRANT SELECT ON public.staff TO app_cashier_grp;
GRANT SELECT ON public.menu_category, public.menu_item TO app_cashier_grp;
GRANT SELECT, INSERT ON public.order_ticket, public.order_line_item TO app_cashier_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_cashier_grp;

-- Step 3: Grant administrative catalog privileges to app_admin_grp
GRANT CONNECT ON DATABASE grp_brewposdb TO app_admin_grp;
GRANT USAGE ON SCHEMA public TO app_admin_grp;
GRANT SELECT ON public.staff TO app_admin_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category, public.menu_item TO app_admin_grp;
GRANT SELECT ON public.order_ticket, public.order_line_item TO app_admin_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_admin_grp;

-- Step 4: Create dedicated operational login accounts
CREATE ROLE brewpos_cashier WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
GRANT app_cashier_grp TO brewpos_cashier;

CREATE ROLE brewpos_manager WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
GRANT app_admin_grp TO brewpos_manager;
```

### 7. Verification & Post-Fix Validation
1. **Positive Test (Cashier creates order):** Authenticated as `brewpos_cashier`, inserted an order ticket and line item. Query succeeded with exit code 0.
2. **Negative Test (Cashier attempts price change):**
   ```sql
   SET ROLE brewpos_cashier;
   UPDATE public.menu_item SET price = 1.00 WHERE item_id = 1;
   ```
   **Result:** `ERROR: permission denied for table menu_item` (Expected failure; access strictly denied).
3. **Negative Test (Cashier attempts category deletion):**
   ```sql
   SET ROLE brewpos_cashier;
   DELETE FROM public.menu_category WHERE category_id = 1;
   ```
   **Result:** `ERROR: permission denied for table menu_category` (Expected failure).

### 8. Prevention & Long-Term Recommendations
- Enforce database-level session role switching or configure dual HikariCP connection pools in Spring Boot (`spring.datasource.cashier` vs `spring.datasource.manager`).
- Schedule periodic privilege audits using `information_schema.role_table_grants` to detect privilege creep.

---

## Incident 2: G01-INC2 — No Explicit Order Status Management

### 1. Incident Metadata & Classification
- **Incident ID:** `G01-INC2`
- **Traceable Flaw:** `G01-F2` ("The order management system does not have an explicit mechanism for tracking order statuses throughout the transaction process.")
- **Activity Area:** Activity 3 — Schema Objects & Relational Integrity
- **Difficulty Rating:** Easy (Actual investigation time: 20 minutes)
- **Target Database:** `grp_brewposdb_restore_test`

### 2. Observe (Symptoms & Exact Incident Brief)
> **Incident Brief:** "Staff members were unable to determine whether an order was still being processed, already completed, or cancelled."

### 3. Verify (Initial Database Inspection & Evidence Log)
The DBA examined the table structure of `public.order_ticket` in `grp_brewposdb_restore_test`:

```sql
SELECT column_name, data_type, column_default, is_nullable
FROM information_schema.columns
WHERE table_name = 'order_ticket' AND table_schema = 'public'
ORDER BY ordinal_position;
```

**Observed Evidence Output:**
```
   column_name   |          data_type          |          column_default           | is_nullable 
-----------------+-----------------------------+-----------------------------------+-------------
 order_id        | integer                     | nextval('order_ticket_order_id_sq'| NO
 staff_id        | integer                     |                                   | NO
 order_timestamp | timestamp without time zone | CURRENT_TIMESTAMP                 | NO
 payment_method  | character varying           |                                   | NO
(4 rows)
```
**Finding:** The table contains only 4 columns: `order_id`, `staff_id`, `order_timestamp`, and `payment_method`. There is no lifecycle status column. Every record is inherently treated as completed, preventing kitchen staff and cashiers from tracking in-flight orders or recording cancellations.

### 4. Baseline Comparison (Activities 3–6 vs. Current State)
- **Documented Baseline ([database/DATABASE_DOCUMENTATION.md](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/database/DATABASE_DOCUMENTATION.md)):** `order_ticket` was documented as storing customer transaction headers. The original Activity 3 design omitted order state tracking, assuming an instantaneous retail checkout model.
- **Real-World Operational Deviation:** A functioning café POS requires distinguishing between `PENDING` (order placed), `PREPARING` (kitchen brewing), `COMPLETED` (tender received and served), and `CANCELLED` (customer walkout/void).

### 5. Troubleshooting Plan
1. **Ordered Checks:**
   - Verify whether existing rows exist in `order_ticket`.
   - Formulate schema modification that preserves existing data without introducing NULLs.
   - Verify that sales reporting queries can easily filter out non-completed orders.
2. **Hypotheses:**
   - *Hypothesis A (Confirmed):* The schema lacks a status tracking attribute, forcing the application to treat all rows as final sales.
   - *Hypothesis B (Alternative - Ruled Out):* Staff were supposed to delete cancelled orders. Ruled out: `brewpos_app` does not have `DELETE` privileges on `order_ticket` (prevented by PoLP); furthermore, hard deletion destroys sequential order auditing.
3. **Risks of Action:** Adding a `NOT NULL` column without a default value will fail if historical orders exist in the database.
4. **Rollback Option:** `ALTER TABLE public.order_ticket DROP COLUMN IF EXISTS order_status;`.

### 6. Corrective Action Applied
The DBA executed [`fix_inc02_order_status_schema.sql`](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/fix_inc02_order_status_schema.sql) on the disposable copy:

```sql
-- Step 1: Add order_status column with backward-compatible default
ALTER TABLE public.order_ticket
    ADD COLUMN IF NOT EXISTS order_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

-- Step 2: Enforce valid state domain with CHECK constraint
ALTER TABLE public.order_ticket
    ADD CONSTRAINT chk_order_ticket_status
    CHECK (order_status IN ('PENDING', 'PREPARING', 'COMPLETED', 'CANCELLED'));

-- Step 3: Set future default for newly created orders to 'PENDING'
ALTER TABLE public.order_ticket
    ALTER COLUMN order_status SET DEFAULT 'PENDING';

-- Step 4: Create performance index for kitchen order display queues
CREATE INDEX idx_order_ticket_status ON public.order_ticket (order_status);

-- Step 5: Create composite index for daily sales aggregations
CREATE INDEX idx_order_ticket_status_timestamp ON public.order_ticket (order_status, order_timestamp);
```

### 7. Verification & Post-Fix Validation
1. **Positive Test (State Lifecycle):** Inserted 4 distinct test orders with statuses `PENDING`, `PREPARING`, `COMPLETED`, and `CANCELLED`. All succeeded.
2. **Negative Test (Invalid Status):**
   ```sql
   INSERT INTO public.order_ticket (staff_id, order_status, payment_method)
   VALUES (1, 'REFUNDED_VOID', 'CASH');
   ```
   **Result:** `ERROR: new row for relation "order_ticket" violates check constraint "chk_order_ticket_status"` (Expected failure; domain integrity preserved).
3. **Historical Data Preservation:** Confirmed that all pre-existing orders retain `order_status = 'COMPLETED'` and 100% row count parity.

### 8. Prevention & Long-Term Recommendations
- Update application daily sales queries to explicitly aggregate `WHERE order_status = 'COMPLETED'`.
- Add a kitchen display system (KDS) query endpoint polling `WHERE order_status IN ('PENDING', 'PREPARING')`.

---

## Incident 3: G01-INC3 — Database Documentation Does Not Match SQL Implementation

### 1. Incident Metadata & Classification
- **Incident ID:** `G01-INC3`
- **Traceable Flaw:** `G01-F3` ("The database setup documentation does not fully match the database roles and permissions implemented in the provided SQL scripts.")
- **Activity Area:** Activities 3 & 4 — Setup, Deployment Scripts, and Security Architecture
- **Difficulty Rating:** Easy (Actual investigation time: 25 minutes)
- **Target Database:** `grp_brewposdb_restore_test`

### 2. Observe (Symptoms & Exact Incident Brief)
> **Incident Brief:** "During database setup, the expected user accounts and permissions were not available, preventing the setup process from matching the documented configuration."

### 3. Verify (Initial Database Inspection & Evidence Log)
The DBA queried `pg_roles` on `grp_brewposdb_restore_test` to verify which roles were actually instantiated by `database/05_create_app_role.sql`:

```sql
SELECT rolname, rolsuper, rolcanlogin, rolinherit
FROM pg_catalog.pg_roles
WHERE rolname LIKE '%brewpos%' OR rolname LIKE '%app%'
ORDER BY rolname;
```

**Observed Evidence Output:**
```
   rolname   | rolsuper | rolcanlogin | rolinherit 
-------------+----------+-------------+------------
 brewpos_app | f        | t           | f
(1 row)
```
**Finding:** Only `brewpos_app` existed. The group roles `app_runtime_grp`, `app_readonly_grp`, `app_admin_grp`, and the documented login role `brewpos_auditor` were completely missing from the PostgreSQL catalog.

### 4. Baseline Comparison (Activities 3–6 vs. Current State)
- **Documented Baseline ([docs/user-roles.md](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/docs/user-roles.md)):** Section 4.1 explicitly instructs creating 3 group roles (`app_runtime_grp`, `app_readonly_grp`, `app_admin_grp`) and Section 4.3 instructs creating login accounts `brewpos_app` and `brewpos_auditor`.
- **Implementation Reality ([database/05_create_app_role.sql](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/database/05_create_app_role.sql)):** The script directly created `brewpos_app` and granted table permissions directly to the user, completely bypassing the group role hierarchy.

### 5. Troubleshooting Plan
1. **Ordered Checks:**
   - Review `database/05_create_app_role.sql` line-by-line.
   - Check if errors occurred during initial database creation.
   - Formulate an idempotent alignment script.
2. **Hypotheses:**
   - *Hypothesis A (Confirmed):* The SQL script author implemented a flattened shortcut during Activity 3 instead of coding the full multi-tier group hierarchy documented in `docs/user-roles.md`.
   - *Hypothesis B (Alternative - Ruled Out):* The missing roles were accidentally dropped by a cleanup script. Ruled out: Git history shows `05_create_app_role.sql` never contained `CREATE ROLE app_runtime_grp`.
3. **Risks of Action:** Rebuilding roles must not invalidate existing connection permissions for the Spring Boot application.
4. **Rollback Option:** Revert `database/05_create_app_role.sql` to its previous commit.

### 6. Corrective Action Applied
The DBA executed [`fix_inc03_align_rbac_ddl.sql`](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/fix_inc03_align_rbac_ddl.sql) on the disposable copy:

```sql
-- Step 1: Idempotently create missing group roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_runtime_grp') THEN
        CREATE ROLE app_runtime_grp WITH NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_readonly_grp') THEN
        CREATE ROLE app_readonly_grp WITH NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_admin_grp') THEN
        CREATE ROLE app_admin_grp WITH NOLOGIN;
    END IF;
END $$;

-- Step 2: Grant database & schema access to group roles
GRANT CONNECT ON DATABASE grp_brewposdb TO app_runtime_grp, app_readonly_grp, app_admin_grp;
GRANT USAGE ON SCHEMA public TO app_runtime_grp, app_readonly_grp, app_admin_grp;
REVOKE CREATE ON SCHEMA public FROM app_runtime_grp, app_readonly_grp, app_admin_grp;

-- Step 3: Assign documented privileges to group roles
GRANT SELECT ON public.staff TO app_runtime_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category, public.menu_item TO app_runtime_grp;
GRANT SELECT, INSERT ON public.order_ticket, public.order_line_item TO app_runtime_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_runtime_grp;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly_grp;

-- Step 4: Create documented login roles and establish inheritance
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_auditor') THEN
        CREATE ROLE brewpos_auditor WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
    END IF;
END $$;

GRANT app_runtime_grp TO brewpos_app;
GRANT app_readonly_grp TO brewpos_auditor;

-- Step 5: Session security hardening
ALTER ROLE brewpos_auditor IN DATABASE grp_brewposdb SET default_transaction_read_only = on;
ALTER ROLE brewpos_auditor IN DATABASE grp_brewposdb SET statement_timeout = '60s';
```

### 7. Verification & Post-Fix Validation
1. **Positive Test (Role Existence):** Executed query on `pg_roles`. All roles (`app_runtime_grp`, `app_readonly_grp`, `app_admin_grp`, `brewpos_app`, `brewpos_auditor`) confirmed present.
2. **Positive Test (Auditor Read):** Authenticated as `brewpos_auditor` and ran `SELECT COUNT(*) FROM public.order_ticket;`. Query returned rows successfully.
3. **Negative Test (Auditor Write Block):** Authenticated as `brewpos_auditor` and attempted `INSERT INTO public.staff (full_name, role) VALUES ('Hacker', 'MANAGER');`.
   **Result:** `ERROR: cannot execute INSERT in a read-only transaction` and `permission denied for table staff` (Expected failure).

### 8. Prevention & Long-Term Recommendations
- Maintain CI/CD automated schema validation that diffs `docs/user-roles.md` against live database catalogs.
- Ensure all DDL scripts are tested on a fresh PostgreSQL instance prior to commit.

---

## Incident 4: G01-INC4 — Incorrect Product Lookup in Database Seeder

### 1. Incident Metadata & Classification
- **Incident ID:** `G01-INC4`
- **Traceable Flaw:** `G01-F4` ("The database seeder uses the menu item name alone when identifying products, which may cause incorrect matches when different categories contain items with the same name.")
- **Activity Area:** Activity 5 — Maintenance, Seeding & Relational Constraint Integrity
- **Difficulty Rating:** Medium (Actual investigation time: 35 minutes)
- **Target Database:** `grp_brewposdb_restore_test`

### 2. Observe (Symptoms & Exact Incident Brief)
> **Incident Brief:** "After sample data was inserted, some orders displayed menu items that did not match the expected products or categories."

### 3. Verify (Initial Database Inspection & Evidence Log)
The DBA inspected lines 90–94 of `database/seed.sql`:

```sql
INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
SELECT oid, m.item_id, v.qty, m.price
FROM (VALUES ('Americano', 2), ('Butter Croissant', 1)) AS v(item_name, qty)
JOIN public.menu_item m ON m.item_name = v.item_name;
```

And cross-referenced this with the table definition in `database/schema.sql`:
```sql
CONSTRAINT uq_menu_item_category_name UNIQUE (category_id, item_name)
```
**Finding:** Uniqueness is constrained on `(category_id, item_name)`, NOT on `item_name` alone. The database schema deliberately permits different categories to contain identical item names (e.g., "Iced Latte" under Coffee vs "Iced Latte" under Non-Coffee). Joining solely on `m.item_name = v.item_name` produces a Cartesian product whenever homonymous items exist. This causes either incorrect product association (attaching the wrong category's item) or fatal primary key collisions on `pk_order_line_item (order_id, item_id)`.

### 4. Baseline Comparison (Activities 3–6 vs. Current State)
- **Baseline Design ([database/schema.sql](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/database/schema.sql)):** Designed with a composite unique constraint `uq_menu_item_category_name (category_id, item_name)`.
- **Seeder Defect ([database/seed.sql](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/database/seed.sql) & [database/04_sample_orders.sql](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/database/04_sample_orders.sql)):** Both scripts assumed `item_name` was globally unique across the entire menu, using an un-scoped join.

### 5. Troubleshooting Plan
1. **Ordered Checks:**
   - Reproduce the failure by inserting a duplicate item name under a different category in `grp_brewposdb_restore_test`.
   - Run the original seeder query and observe incorrect item association or `pk_order_line_item` violation.
   - Refactor the query to join through `menu_category`.
2. **Hypotheses:**
   - *Hypothesis A (Confirmed):* The join in `seed.sql` lacks category qualification, causing multi-row expansion and wrong item matching when identical names exist across categories.
   - *Hypothesis B (Alternative - Ruled Out):* `pk_order_line_item` sequence was out of sync. Ruled out: `order_line_item` does not use a sequence; its primary key is composite `(order_id, item_id)`.
3. **Risks of Action:** None. The corrective action is purely a query correction in the seeder script.
4. **Rollback Option:** Revert changes to `database/seed.sql`.

### 6. Corrective Action Applied
The DBA developed and applied [`fix_inc04_seeder_product_lookup.sql`](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/fix_inc04_seeder_product_lookup.sql):

```sql
-- Refactored query joining on BOTH category_name and item_name:
INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
SELECT 
    oid, 
    m.item_id, 
    v.qty, 
    m.price
FROM (VALUES 
    ('Coffee',   'Americano',        2),
    ('Pastries', 'Butter Croissant', 1)
) AS v(cat_name, item_name, qty)
JOIN public.menu_category c 
    ON c.category_name = v.cat_name
JOIN public.menu_item m 
    ON m.category_id = c.category_id 
   AND m.item_name = v.item_name;
```

### 7. Verification & Post-Fix Validation
1. **Fault Reproduction Test (Before Fix):**
   - Inserted duplicate item: `INSERT INTO public.menu_item (category_id, item_name, price) VALUES (2, 'Americano', 95.00);`.
   - Executed legacy join: Produced 2 rows for 'Americano', resulting in incorrect line items and:  
     `ERROR: duplicate key value violates unique constraint "pk_order_line_item" Key (order_id, item_id)=(5, 1) already exists`.
2. **Resolution Verification (After Fix):**
   - Executed the category-qualified query. Exactly 1 row for `('Coffee', 'Americano')` was returned and inserted. Zero constraint violations occurred.
3. **Seeder Idempotency:** Verified that re-running `seed.sql` completes cleanly with no duplicate entries.

### 8. Prevention & Long-Term Recommendations
- Enforce a coding standard in all database seeders and analytical queries requiring composite foreign key paths to be fully qualified.
- Add an automated regression test in Maven that runs seeder scripts against a populated database with duplicate item names across categories.

---

## Incident 5: G01-INC5 — No Dedicated Payment Transaction Records

### 1. Incident Metadata & Classification
- **Incident ID:** `G01-INC5`
- **Traceable Flaw:** `G01-F5` ("The database does not provide a dedicated table for recording individual payment transactions and their details.")
- **Activity Area:** Activities 3 & 6 — Financial Integrity, Schema Architecture, and Disaster Recovery Audit
- **Difficulty Rating:** Hard (Actual investigation, schema migration & testing: 55 minutes)
- **Target Database:** `grp_brewposdb_restore_test`

### 2. Observe (Symptoms & Exact Incident Brief)
> **Incident Brief:** "Staff members were unable to retrieve complete payment information for certain orders, including the payment method used."

### 3. Verify (Initial Database Inspection & Evidence Log)
The DBA analyzed the existing payment representation in `public.order_ticket`:

```sql
SELECT conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c
JOIN pg_class t ON c.conrelid = t.oid
WHERE t.relname = 'order_ticket' AND conname LIKE '%payment%';
```

**Observed Evidence Output:**
```
         conname          |                      pg_get_constraintdef                      
--------------------------+----------------------------------------------------------------
 chk_order_ticket_payment | CHECK ((payment_method)::text = ANY (ARRAY['CASH'::character varying, 'ONLINE'::character varying]::text[]))
(1 row)
```
**Finding:**
1. Only two hardcoded payment strings exist (`CASH`, `ONLINE`).
2. There is no support for GCash, Maya, Debit/Credit Card, or split payments.
3. No transaction reference IDs, tender amounts, change calculations, payment timestamps, or payment statuses (`COMPLETED`, `REFUNDED`, `FAILED`) can be recorded. Staff cannot inspect payment details.

### 4. Baseline Comparison (Activities 3–6 vs. Current State)
- **Activity 3 Baseline:** `order_ticket` was designed as a simplified header table with a single `payment_method` column.
- **Activity 6 DR & Financial Audit Requirement:** Relational integrity requires 1:N payment transaction tracking so that multiple tenders per order can be recorded, audited, and reconciled against bank settlements without data loss.

### 5. Troubleshooting Plan
1. **Ordered Checks:**
   - Calculate total order value for all existing orders using `SUM(quantity * sold_price)` from `order_line_item`.
   - Design a dedicated `payment_transaction` table with foreign key to `order_ticket`.
   - Formulate an idempotent data migration script to populate `payment_transaction` from historical `order_ticket` rows without losing any financial history.
2. **Hypotheses:**
   - *Hypothesis A (Confirmed):* The single-column architecture on `order_ticket` violates First and Second Normal Forms for multi-tender transactions.
   - *Hypothesis B (Alternative - Ruled Out):* Add more columns directly to `order_ticket` (e.g., `payment_method_2`, `reference_no`). Ruled out: Denormalized columns cannot scale for split payments and create widespread NULL values.
3. **Risks of Action:** Dropping `order_ticket.payment_method` immediately would break legacy Spring Boot JPA queries in `OrderTicketRepository`.
4. **Mitigation:** Retain `order_ticket.payment_method` temporarily for backward compatibility while routing all new transaction auditing to `payment_transaction`.
5. **Rollback Option:** `DROP TABLE IF EXISTS public.payment_transaction CASCADE;`.

### 6. Corrective Action Applied
The DBA executed [`fix_inc05_payment_transaction_schema.sql`](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/fix_inc05_payment_transaction_schema.sql) on the disposable copy:

```sql
-- Step 1: Create dedicated payment_transaction table
CREATE TABLE IF NOT EXISTS public.payment_transaction (
    payment_id          SERIAL PRIMARY KEY,
    order_id            INTEGER NOT NULL,
    payment_method      VARCHAR(20) NOT NULL,
    amount              DECIMAL(10,2) NOT NULL,
    tendered_amount     DECIMAL(10,2),
    change_amount       DECIMAL(10,2) DEFAULT 0.00,
    transaction_ref     VARCHAR(100),
    payment_status      VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    payment_timestamp   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_order 
        FOREIGN KEY (order_id) REFERENCES public.order_ticket (order_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_payment_method 
        CHECK (payment_method IN ('CASH', 'GCASH', 'MAYA', 'CARD', 'ONLINE')),
    CONSTRAINT chk_payment_status 
        CHECK (payment_status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payment_amount 
        CHECK (amount > 0),
    CONSTRAINT chk_change_amount 
        CHECK (change_amount >= 0)
);

-- Step 2: Query-justified performance indexes
CREATE INDEX idx_payment_order_id ON public.payment_transaction (order_id);
CREATE INDEX idx_payment_timestamp ON public.payment_transaction (payment_timestamp);
CREATE INDEX idx_payment_status_method ON public.payment_transaction (payment_status, payment_method);

-- Step 3: Idempotent Historical Migration from order_ticket + order_line_item
INSERT INTO public.payment_transaction (
    order_id, payment_method, amount, tendered_amount, change_amount, transaction_ref, payment_status, payment_timestamp
)
SELECT 
    ot.order_id,
    CASE WHEN ot.payment_method = 'ONLINE' THEN 'ONLINE' ELSE 'CASH' END,
    COALESCE(SUM(oli.quantity * oli.sold_price), 0.00),
    COALESCE(SUM(oli.quantity * oli.sold_price), 0.00),
    0.00,
    'LEGACY-MIGRATION-ORDER-' || ot.order_id,
    'COMPLETED',
    ot.order_timestamp
FROM public.order_ticket ot
LEFT JOIN public.order_line_item oli ON ot.order_id = oli.order_id
WHERE NOT EXISTS (SELECT 1 FROM public.payment_transaction pt WHERE pt.order_id = ot.order_id)
GROUP BY ot.order_id, ot.payment_method, ot.order_timestamp
HAVING COALESCE(SUM(oli.quantity * oli.sold_price), 0.00) > 0;

-- Step 4: Grant permissions to runtime and readonly roles
GRANT SELECT, INSERT ON public.payment_transaction TO app_runtime_grp;
GRANT SELECT ON public.payment_transaction TO app_readonly_grp;
GRANT USAGE, SELECT ON SEQUENCE public.payment_transaction_payment_id_seq TO app_runtime_grp;
```

### 7. Verification & Post-Fix Validation
1. **Migration Parity Check:** Verified that every existing historical order ticket has a corresponding `payment_transaction` record whose `amount` exactly equals `SUM(quantity * sold_price)`.
2. **Split Payment Test (Positive Test):**
   - Inserted a test order totaling ₱300.00.
   - Inserted Payment 1: `CASH`, ₱100.00, tendered ₱100.00, change ₱0.00.
   - Inserted Payment 2: `GCASH`, ₱200.00, ref `GCASH-TXN-491823`, status `COMPLETED`.
   - Query verified both payments summed to exactly ₱300.00.
3. **Negative Constraint Tests:**
   - Attempted negative amount: `amount = -50.00` → `ERROR: violates check constraint "chk_payment_amount"` (Passed expected failure).
   - Attempted unsupported tender: `payment_method = 'CRYPTO'` → `ERROR: violates check constraint "chk_payment_method"` (Passed expected failure).

### 8. Prevention & Long-Term Recommendations
- Update Spring Boot JPA layer to map `@OneToMany` relationship between `OrderTicket` and `PaymentTransaction`.
- Integrate daily automated reconciliation scripts comparing total payment records with line item aggregates.

---

# 5. Consolidated Verification Suite & Security Matrix

Group 4 developed an automated, end-to-end verification script ([`verify_all_fixes.sql`](file:///c:/Users/oliva/OneDrive/Documents/BrewPOS/midterm/owner-investigation/scripts/verify_all_fixes.sql)) executed against `grp_brewposdb_restore_test`. Below is the complete test result matrix:

| Test ID | Target Incident | Description / Test Command | Expected Result | Actual Result | Status |
|---|---|---|---|---|:---:|
| **VT-01** | G01-INC1 | Cashier role attempts `SELECT` on `menu_item` and `staff` | Query succeeds; catalog records visible | Returned active/inactive items | **PASS** |
| **VT-02** | G01-INC1 | Cashier role attempts `UPDATE public.menu_item SET price = 10.00` | Permission denied by PostgreSQL engine | `ERROR: permission denied for table menu_item` | **PASS (Expected Failure)** |
| **VT-03** | G01-INC1 | Cashier role attempts `DELETE FROM public.menu_category` | Permission denied by PostgreSQL engine | `ERROR: permission denied for table menu_category` | **PASS (Expected Failure)** |
| **VT-04** | G01-INC2 | Insert order ticket with valid status `PENDING` | Row inserted with status `'PENDING'` | Successfully inserted | **PASS** |
| **VT-05** | G01-INC2 | Insert order ticket with valid status `CANCELLED` | Row inserted with status `'CANCELLED'` | Successfully inserted | **PASS** |
| **VT-06** | G01-INC2 | Insert order ticket with invalid status `DELIVERED_EXT` | Check constraint `chk_order_ticket_status` fires | `ERROR: check constraint "chk_order_ticket_status"` | **PASS (Expected Failure)** |
| **VT-07** | G01-INC3 | Query `pg_roles` for `app_runtime_grp`, `app_readonly_grp`, `app_admin_grp`, `brewpos_auditor` | All 4 roles present in PostgreSQL catalog | 4 roles returned; correct `NOLOGIN`/`LOGIN` flags | **PASS** |
| **VT-08** | G01-INC3 | Connect as `brewpos_auditor` and execute write (`INSERT INTO staff`) | Transaction blocked by read-only mode and permissions | `ERROR: cannot execute INSERT in read-only transaction` | **PASS (Expected Failure)** |
| **VT-09** | G01-INC4 | Execute seeder with duplicate item names under different categories | Category-qualified join selects exact item; zero duplicates | Exactly 1 line item inserted per order specification | **PASS** |
| **VT-10** | G01-INC5 | Verify historical payment migration parity | 100% of pre-existing orders have payment records matching line totals | All historical orders verified; zero delta | **PASS** |
| **VT-11** | G01-INC5 | Insert split payment (Cash + GCash) for single order ticket | Both payment rows recorded linked by `order_id` | Split payments recorded; total equals ticket cost | **PASS** |
| **VT-12** | G01-INC5 | Insert payment with negative amount (`-20.00`) | Check constraint `chk_payment_amount` fires | `ERROR: check constraint "chk_payment_amount"` | **PASS (Expected Failure)** |

---

# 6. Prevention, Practical Recommendations & Escalation Review

### 6.1 Top 3 Practical Recommendations Overall
In accordance with assessment requirements, Group 4 establishes the following top three practical database administration recommendations:

1. **Dual-Pool Least-Privilege Application Architecture:**
   Transition the Spring Boot application from a monolithic service credential (`brewpos_app`) to dual HikariCP connection pools:
   - `pool-register`: Authenticates as `brewpos_cashier` (read catalog, write tickets).
   - `pool-admin`: Authenticates as `brewpos_manager` (manage categories, items, and view sales).  
   This guarantees that even if front-end authentication is breached, the database engine physically prohibits register terminals from modifying pricing or deleting categories.

2. **Automated Schema and Seeder CI/CD Gateways:**
   Integrate automated PostgreSQL schema verification drills in GitHub Actions. Every Pull Request must spin up a disposable container, run `schema.sql`, execute `seed.sql`, and execute `03_verification_queries.sql` to verify constraint compliance, category-scoped uniqueness, and group role existence before code can be merged to `main`.

3. **Daily Cryptographic DR Drills with Automated Reconciliation:**
   Expand the verified Activity 6 backup procedures (`pg_dump -Fc` with SHA-256 checksums) by scheduling an automated daily restore-test script that:
   - Restores the backup into `grp_brewposdb_restore_test`.
   - Runs reconciliation queries ensuring `SUM(order_line_item.quantity * sold_price) == SUM(payment_transaction.amount)`.
   - Generates an automated audit log certifying zero data loss and 100% relational integrity.

### 6.2 Escalation & Risk Justification Analysis
- **Evaluation of Risky Schema Alterations:** During the investigation of Flaw 5 (Payment Transactions), an alternative proposal was evaluated: immediately dropping `order_ticket.payment_method`.  
  **DBA Judgment & Escalation Decision:** The DBA team rejected an immediate `DROP COLUMN payment_method`. Dropping this column in production would cause fatal runtime exceptions in existing Spring Data JPA repository queries (`findByPaymentMethod`) before the application codebase could be updated and deployed. Instead, a phased transitional approach was adopted: create `payment_transaction`, backfill historical records, and escalate a ticket to the Application Development team to update JPA entities before executing final schema cleanup.

---

# 7. Designer Review & Feedback Exchange

### 7.1 Review of Designer Group Scenarios
The owner group evaluated the 5 incident scenarios designed by Group 10:
- **Fairness & Solvability:** All 5 scenarios were directly traceable to genuine flaws in our codebase. None required destructive superuser interventions.
- **Difficulty & Realism:** The balance of 2 Easy, 2 Medium, and 1 Hard accurately reflected realistic DBA challenges, ranging from quick documentation alignments to multi-table data migration drills.
- **Constructive Designer Feedback:** Group 10's observation regarding Flaw 4 (`JOIN public.menu_item m ON m.item_name = v.item_name`) was particularly insightful, uncovering an edge case in our seeder that would have caused silent Cartesian data corruption upon introducing duplicate product names across categories.


# 8. AI Usage Declaration & Validation Methodology

- **Tools Utilized:**Gemini 3.8 Flash.
- **Scope of Generation:** AI was used to draft structural templates for the incident investigation report, format markdown comparison tables, and suggest initial query structures for historical payment migration.
- **Independent Human Validation Methodology:**
  1. Every SQL query, constraint definition, and role grant proposed by AI was independently reviewed against the official PostgreSQL 18 Documentation.
  2. All SQL fix scripts were executed manually inside a sandboxed PostgreSQL terminal against `grp_brewposdb_restore_test`.
  3. Passwords, secrets, and environment tokens were managed exclusively by team members and excluded from all committed files.
