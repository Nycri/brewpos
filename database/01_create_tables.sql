-- ============================================================================
-- BREWPOS - 01_create_tables.sql
-- DDL for the five-table design. FOR A NEW / EMPTY DATABASE ONLY.
--
-- Do NOT run this against a populated grp_brewposdb. It intentionally has no
-- DROP statements and will fail (not overwrite) if the tables already exist.
--
-- Create the database first, separately (CREATE DATABASE cannot run inside a
-- transaction block), then connect to it:
--     CREATE DATABASE grp_brewposdb;
--     \c grp_brewposdb
-- ============================================================================

-- 1. staff ------------------------------------------------------------------
CREATE TABLE public.staff (
    staff_id   SERIAL       PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_staff_role CHECK (role IN ('CASHIER', 'MANAGER'))
);

-- 2. menu_category ----------------------------------------------------------
CREATE TABLE public.menu_category (
    category_id   SERIAL      PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_menu_category_name UNIQUE (category_name)
);

-- 3. menu_item --------------------------------------------------------------
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

-- 4. order_ticket (named order_ticket because ORDER is a SQL keyword) -------
CREATE TABLE public.order_ticket (
    order_id         SERIAL      PRIMARY KEY,
    staff_id         INTEGER     NOT NULL,
    order_timestamp  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_method   VARCHAR(20) NOT NULL,
    CONSTRAINT fk_order_ticket_staff
        FOREIGN KEY (staff_id) REFERENCES public.staff (staff_id),
    CONSTRAINT chk_order_ticket_payment CHECK (payment_method IN ('CASH', 'ONLINE'))
);

-- 5. order_line_item (composite PK, no surrogate id, no stored totals) ------
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

-- Indexes (justified by actual queries; not additional tables/columns) ------
-- Date-range sales reports and newest-first order history filter/sort on this.
CREATE INDEX idx_order_ticket_timestamp ON public.order_ticket (order_timestamp);
-- The composite PK (order_id, item_id) already serves order-side lookups. This
-- index serves item-side lookups (e.g. "is this item referenced by any order
-- line?" before deleting, and FK checks on menu_item).
CREATE INDEX idx_order_line_item_item_id ON public.order_line_item (item_id);
