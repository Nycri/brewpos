-- ============================================================================
-- BREWPOS — Database Schema & Objects Script (database/schema.sql)
-- Target RDBMS : PostgreSQL 18.6
-- Database     : grp_brewposdb
-- Schema       : public
--
-- PURPOSE:
--   Creates the strictly specified five-table relational schema, primary keys
--   (including composite PK on order_line_item), foreign keys, CHECK and
--   UNIQUE constraints, and query-justified B-tree indexes.
--
-- SAFETY NOTES:
--   - Run against a NEW / EMPTY grp_brewposdb database only.
--   - Contains NO DROP statements so it cannot accidentally destroy existing data.
--   - If grp_brewposdb already exists and needs constraint/index alignment,
--     run database/06_align_existing_db.sql instead.
--
-- EXECUTION INSTRUCTIONS (PowerShell / Terminal):
--   1. Create the database (cannot run inside a transaction block):
--        psql -U postgres -d postgres -c "CREATE DATABASE grp_brewposdb;"
--   2. Create schema tables, constraints, and indexes:
--        psql -U postgres -d grp_brewposdb -f database/schema.sql
--   3. (Recommended) Create least-privilege application role:
--        psql -U postgres -d grp_brewposdb -f database/05_create_app_role.sql
--        psql -U postgres -d grp_brewposdb -c "\password brewpos_app"
--   4. (Optional) Seed initial staff, menu, and sample orders for immediate testing:
--        psql -U postgres -d grp_brewposdb -f database/seed.sql
-- ============================================================================

BEGIN;

-- 1. staff -------------------------------------------------------------------
CREATE TABLE public.staff (
    staff_id   SERIAL       PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_staff_role CHECK (role IN ('CASHIER', 'MANAGER'))
);

-- 2. menu_category -----------------------------------------------------------
CREATE TABLE public.menu_category (
    category_id   SERIAL      PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_menu_category_name UNIQUE (category_name)
);

-- 3. menu_item ---------------------------------------------------------------
CREATE TABLE public.menu_item (
    item_id     SERIAL         PRIMARY KEY,
    category_id INTEGER        NOT NULL,
    item_name   VARCHAR(100)   NOT NULL,
    price       DECIMAL(10,2)  NOT NULL,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_menu_item_category
        FOREIGN KEY (category_id) REFERENCES public.menu_category (category_id),
    CONSTRAINT chk_menu_item_price CHECK (price >= 0),
    CONSTRAINT uq_menu_item_category_name UNIQUE (category_id, item_name)
);

-- 4. order_ticket (named order_ticket because ORDER is a reserved SQL word) --
CREATE TABLE public.order_ticket (
    order_id         SERIAL      PRIMARY KEY,
    staff_id         INTEGER     NOT NULL,
    order_timestamp  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_method   VARCHAR(20) NOT NULL,
    CONSTRAINT fk_order_ticket_staff
        FOREIGN KEY (staff_id) REFERENCES public.staff (staff_id),
    CONSTRAINT chk_order_ticket_payment CHECK (payment_method IN ('CASH', 'ONLINE'))
);

-- 5. order_line_item (composite PK, no surrogate ID, no stored totals) -------
CREATE TABLE public.order_line_item (
    order_id    INTEGER       NOT NULL,
    item_id     INTEGER       NOT NULL,
    quantity    INTEGER       NOT NULL,
    sold_price  DECIMAL(10,2) NOT NULL,
    CONSTRAINT pk_order_line_item PRIMARY KEY (order_id, item_id),
    CONSTRAINT fk_order_line_order
        FOREIGN KEY (order_id) REFERENCES public.order_ticket (order_id),
    CONSTRAINT fk_order_line_item
        FOREIGN KEY (item_id) REFERENCES public.menu_item (item_id),
    CONSTRAINT chk_order_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_line_sold_price CHECK (sold_price >= 0)
);

-- 6. Query-justified performance indexes -------------------------------------
-- Supports half-open [start, end) daily sales reports and newest-first order history
CREATE INDEX idx_order_ticket_timestamp ON public.order_ticket (order_timestamp);

-- Supports item-side lookups and FK enforcement on menu_item (since composite PK leads with order_id)
CREATE INDEX idx_order_line_item_item_id ON public.order_line_item (item_id);

COMMIT;

ANALYZE public.staff;
ANALYZE public.menu_category;
ANALYZE public.menu_item;
ANALYZE public.order_ticket;
ANALYZE public.order_line_item;

