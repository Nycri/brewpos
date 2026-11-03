# BrewPOS — Application User Roles & Database Role Mapping
**Course:** COMP 018 — Database Administration  
**Project:** BrewPOS — Cafe Point-of-Sale System  


---

## 1. Overview and Core Principle

In accordance with the guidelines, **Application User Roles** (what a human user can see and do inside the web application) are strictly decoupled from **Database Roles** (what PostgreSQL accounts and privileges exist at the database engine level).

End users (cashiers and managers) do **not** possess individual PostgreSQL login accounts. Instead, the Spring Boot application connects to the PostgreSQL database (`grp_brewposdb`) via a single, dedicated runtime service account (`brewpos_app`). Database-level privileges enforce least-privilege access, ensuring that even if an application-layer check were bypassed, the database engine prohibits unauthorized operations (such as updating or deleting historical sales records).

---

## 2. Roles Mapping Matrix

The table below defines how each application user role maps to the intended PostgreSQL database group roles and accounts:

| Application Role | Responsibilities in the Application | Functions and Data Access | Intended Database Role (Mapping) |
|---|---|---|---|
| **Store Manager** *(Administrator)* | Oversees café product catalog, manages menu items and categories, adjusts item prices, toggles item availability (active/inactive), and reviews financial sales reports. | • Full CRUD on `menu_category`<br>• Full CRUD on `menu_item`<br>• Activate / deactivate menu items<br>• View receipts and aggregated daily sales<br>• *Restricted from altering or deleting completed orders* | `app_admin_grp` *(or elevated operational runtime role)* |
| **Cashier** *(Staff / Data Entry)* | Operates front-of-house register during daily business hours, browses active menu items, assembles customer orders, selects payment tender (Cash or Online), and issues receipts. | • Read-only access to `staff`<br>• Read-only access to active `menu_item`<br>• Create (INSERT) `order_ticket`<br>• Create (INSERT) `order_line_item`<br>• View completed receipts<br>• *Restricted from modifying menu prices, categories, or financial summaries* | `app_runtime_grp` *(Operational register runtime role)* |
| **Viewer / Auditor** | Performs accounting audits, financial inspection, tax reconciliation, and verifies historical transaction integrity. | • Read-only (SELECT) on all 5 tables: `staff`, `menu_category`, `menu_item`, `order_ticket`, `order_line_item`<br>• Zero write, update, or delete privileges | `app_readonly_grp` *(Auditing group role)* |
| **Application Runtime** *(System Account)* | The programmatic service account used by Spring Boot to connect to `grp_brewposdb` via HikariCP connection pooling. | • SELECT on `staff`<br>• SELECT, INSERT, UPDATE, DELETE on `menu_category` and `menu_item`<br>• SELECT, INSERT on `order_ticket` and `order_line_item`<br>• *Strictly NO UPDATE or DELETE on order tickets or line items* | `brewpos_app` *(Member of `app_runtime_grp`)* |

---

## 3. Detailed Application User Roles

### 3.1 Store Manager
- **Role Purpose:** Responsible for business administration, pricing strategy, and menu engineering.
- **Application Capabilities:**
  - **Menu Categories:** Create new categories, rename existing categories, and safely delete unused categories (deletion is blocked with HTTP `409 Conflict` if menu items reference the category).
  - **Menu Items:** Add new products with name, category, and price (`DECIMAL(10,2)`). Edit item details and prices at any time.
  - **Active/Inactive Status Toggle:** Deactivate seasonal or out-of-stock items so they are hidden from the Register while preserving all historical order references. Reactivate items when available.
  - **Daily Sales Analytics:** View daily gross revenue, transaction counts, and Cash vs. Online revenue share bars.
  - **Audit Safeguard:** Managers **cannot** edit or delete completed order tickets or line items. Historical sales data is immutable.

### 3.2 Cashier
- **Role Purpose:** Responsible for fast, error-free customer order entry and tender collection.
- **Application Capabilities:**
  - **Register View:** Browse active products grouped by category with real-time text search.
  - **Order Assembly:** Tap items to add to the current ticket. Multiple clicks on the same product automatically merge quantities on the composite primary key `(order_id, item_id)`.
  - **Tender Recording:** Choose exactly one payment method (`Cash` or `Online`) per order.
  - **Checkout:** Submit order in an atomic transaction; review itemized receipt modal with unit sold prices and total.
  - **UI Safeguards:** When Cashier mode is active, category and item creation forms, edit buttons, and delete buttons are completely hidden from the user interface.

### 3.3 Viewer / Auditor
- **Role Purpose:** Independent review of financial transactions, sales reports, and database integrity.
- **Application Capabilities:**
  - Inspect transaction timestamps, cashier attributions, payment methods, and historical unit sold prices.
  - Execute read-only verification queries (e.g., [database/03_verification_queries.sql](../database/03_verification_queries.sql)) to reconcile reported daily sales against raw database aggregations (`SUM(quantity * sold_price)`).

---

## 4. Intended PostgreSQL Database RBAC Setup

To prepare for succeeding database administration activities (Activity 4), the PostgreSQL database implements Role-Based Access Control (RBAC) following the **Principle of Least Privilege**:

### 4.1 Database Group Roles (`NOLOGIN`)
```sql
-- 1. Create Group Roles
CREATE ROLE app_runtime_grp  WITH NOLOGIN;
CREATE ROLE app_readonly_grp WITH NOLOGIN;
CREATE ROLE app_admin_grp    WITH NOLOGIN;
```

### 4.2 Privileges Matrix
```sql
-- Connect and schema usage
GRANT CONNECT ON DATABASE grp_brewposdb TO app_runtime_grp, app_readonly_grp, app_admin_grp;
GRANT USAGE ON SCHEMA public TO app_runtime_grp, app_readonly_grp, app_admin_grp;

-- app_runtime_grp (Application Runtime — used by Spring Boot)
GRANT SELECT ON public.staff TO app_runtime_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category, public.menu_item TO app_runtime_grp;
GRANT SELECT, INSERT ON public.order_ticket, public.order_line_item TO app_runtime_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_runtime_grp;

-- app_readonly_grp (Auditing and Reporting)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly_grp;

-- app_admin_grp (Elevated Catalog Management)
GRANT SELECT ON public.staff TO app_admin_grp;
GRANT ALL PRIVILEGES ON public.menu_category, public.menu_item TO app_admin_grp;
GRANT SELECT ON public.order_ticket, public.order_line_item TO app_admin_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_admin_grp;
```

> **Key Security Feature:** Notice that `app_runtime_grp` receives only `SELECT, INSERT` on `order_ticket` and `order_line_item`. Because `UPDATE` and `DELETE` privileges are never granted on sales tables, completed transactions are permanent and tamper-proof at the database engine level.

### 4.3 Database Login Accounts (`WITH LOGIN`)
```sql
-- 1. Dedicated application service account (configured in .env)
CREATE ROLE brewpos_app WITH LOGIN PASSWORD 'CHANGE_IN_LOCAL_ENV';
GRANT app_runtime_grp TO brewpos_app;

-- 2. Dedicated auditor account
CREATE ROLE brewpos_auditor WITH LOGIN PASSWORD 'CHANGE_IN_LOCAL_ENV';
GRANT app_readonly_grp TO brewpos_auditor;

-- 3. Dedicated catalog admin account
CREATE ROLE brewpos_admin WITH LOGIN PASSWORD 'CHANGE_IN_LOCAL_ENV';
GRANT app_admin_grp TO brewpos_admin;
```

*(This configuration is automated in [database/05_create_app_role.sql](../database/05_create_app_role.sql).)*

---

## 5. Security & Credential Isolation

1. **No Hardcoded Passwords:** Real passwords are never committed to Git. The project repository provides [.env.example](../.env.example) with placeholder values only (`DB_PASSWORD=your_password_here`).
2. **Environment Variable Configuration:** The local `.env` file containing actual database credentials is included in [.gitignore](../.gitignore) and remains strictly on the local machine.
3. **No Direct User Database Access:** All end-user requests pass through the Spring Boot REST API layer, which validates business rules, sanitizes input parameters, and uses parameterized JPA/Hibernate queries to prevent SQL injection.

