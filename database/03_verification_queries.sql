-- ============================================================================
-- BREWPOS - 03_verification_queries.sql
-- Read-only checks (the constraint tests run inside a transaction that is
-- rolled back). Run with:  psql -d grp_brewposdb -f 03_verification_queries.sql
-- ============================================================================

-- A. Exactly five application tables in public ------------------------------
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
-- Expect: menu_category, menu_item, order_line_item, order_ticket, staff

-- B. Columns of each table (no extras such as total/status/customer_id) -----
SELECT table_name, ordinal_position, column_name, data_type,
       character_maximum_length, numeric_precision, numeric_scale,
       is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('staff','menu_category','menu_item','order_ticket','order_line_item')
ORDER BY table_name, ordinal_position;

-- C. Primary keys (order_line_item must show order_id, item_id) -------------
SELECT tc.table_name, tc.constraint_name,
       string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS pk_columns
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
WHERE tc.table_schema = 'public' AND tc.constraint_type = 'PRIMARY KEY'
GROUP BY tc.table_name, tc.constraint_name
ORDER BY tc.table_name;

-- D. Foreign keys (expect 4, with NO "ON DELETE CASCADE") --------------------
SELECT conrelid::regclass AS child_table, conname,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'f' AND connamespace = 'public'::regnamespace
ORDER BY 1, 2;

-- E. Unique and check constraints -------------------------------------------
SELECT conrelid::regclass AS table_name, conname, contype,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype IN ('u','c') AND connamespace = 'public'::regnamespace
ORDER BY 1, 2;

-- F. Row counts -------------------------------------------------------------
SELECT 'staff' AS tbl, COUNT(*) FROM public.staff
UNION ALL SELECT 'menu_category',   COUNT(*) FROM public.menu_category
UNION ALL SELECT 'menu_item',       COUNT(*) FROM public.menu_item
UNION ALL SELECT 'order_ticket',    COUNT(*) FROM public.order_ticket
UNION ALL SELECT 'order_line_item', COUNT(*) FROM public.order_line_item;

-- G. Derived order totals (never stored): SUM(quantity * sold_price) --------
SELECT t.order_id, t.order_timestamp, t.payment_method,
       SUM(l.quantity * l.sold_price) AS order_total
FROM public.order_ticket t
JOIN public.order_line_item l ON l.order_id = t.order_id
GROUP BY t.order_id, t.order_timestamp, t.payment_method
ORDER BY t.order_timestamp DESC, t.order_id DESC;

-- H. Daily sales for today (half-open range; replace CURRENT_DATE for other days)
--    Sales total and DISTINCT order count (not line count).
SELECT COALESCE(SUM(l.quantity * l.sold_price), 0) AS total_sales,
       COUNT(DISTINCT t.order_id)                  AS order_count
FROM public.order_ticket t
JOIN public.order_line_item l ON l.order_id = t.order_id
WHERE t.order_timestamp >= CURRENT_DATE
  AND t.order_timestamp <  CURRENT_DATE + 1;

-- I. Daily sales by payment method (totals must add up to query H) ----------
SELECT t.payment_method,
       SUM(l.quantity * l.sold_price) AS total_sales,
       COUNT(DISTINCT t.order_id)     AS order_count
FROM public.order_ticket t
JOIN public.order_line_item l ON l.order_id = t.order_id
WHERE t.order_timestamp >= CURRENT_DATE
  AND t.order_timestamp <  CURRENT_DATE + 1
GROUP BY t.payment_method
ORDER BY t.payment_method;

-- J. Constraint tests: every statement below MUST fail; all rolled back -----
-- Run each block separately (a failed statement aborts the transaction).
-- BEGIN; INSERT INTO public.staff (full_name, role) VALUES ('X', 'OWNER');                       ROLLBACK;  -- bad role
-- BEGIN; INSERT INTO public.staff (full_name, role) VALUES (NULL, 'CASHIER');                    ROLLBACK;  -- null name
-- BEGIN; INSERT INTO public.menu_item (category_id,item_name,price) VALUES (999999,'X',1);       ROLLBACK;  -- bad FK
-- BEGIN; INSERT INTO public.order_ticket (staff_id,payment_method) VALUES (1,'CARD');            ROLLBACK;  -- bad payment
-- BEGIN; INSERT INTO public.order_line_item VALUES (1,1,0,10);                                   ROLLBACK;  -- quantity 0
-- BEGIN; INSERT INTO public.order_line_item VALUES (1,1,1,-1);                                   ROLLBACK;  -- negative sold_price
-- Defaults check (rolled back): is_active TRUE and order_timestamp = now
-- BEGIN;
--   INSERT INTO public.menu_category (category_name) VALUES ('__tmp__');
--   INSERT INTO public.menu_item (category_id,item_name,price)
--     SELECT category_id,'__tmp_item__',1 FROM public.menu_category WHERE category_name='__tmp__'
--     RETURNING is_active;                                -- expect t
-- ROLLBACK;

-- K. Indexes in public (PK/unique + idx_order_ticket_timestamp + idx_order_line_item_item_id)
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- L. Least-privilege application role audit (brewpos_app) -------------------
-- L1. Role security flags (all privilege escalation flags must be FALSE)
SELECT rolname, rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolreplication
FROM pg_roles
WHERE rolname = 'brewpos_app';

-- L2. Table-level privileges granted to brewpos_app
-- Expect:
--   menu_category   -> DELETE, INSERT, SELECT, UPDATE
--   menu_item       -> DELETE, INSERT, SELECT, UPDATE
--   order_line_item -> INSERT, SELECT
--   order_ticket    -> INSERT, SELECT
--   staff           -> SELECT
SELECT c.relname AS table_name,
       string_agg(p.priv, ', ' ORDER BY p.priv) AS privileges
FROM pg_class c
JOIN pg_roles r ON r.rolname = 'brewpos_app'
CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'), ('REFERENCES'), ('TRIGGER')) AS p(priv)
WHERE c.relnamespace = 'public'::regnamespace
  AND c.relkind = 'r'
  AND has_table_privilege(r.oid, c.oid, p.priv)
GROUP BY c.relname
ORDER BY c.relname;

-- L3. Sequence privileges granted to brewpos_app (expect SELECT, USAGE)
SELECT c.relname AS sequence_name,
       string_agg(p.priv, ', ' ORDER BY p.priv) AS privileges
FROM pg_class c
JOIN pg_roles r ON r.rolname = 'brewpos_app'
CROSS JOIN (VALUES ('SELECT'), ('USAGE'), ('UPDATE')) AS p(priv)
WHERE c.relnamespace = 'public'::regnamespace
  AND c.relkind = 'S'
  AND has_sequence_privilege(r.oid, c.oid, p.priv)
GROUP BY c.relname
ORDER BY c.relname;

-- M. Read-only query plan check for daily sales date-range report -----------
EXPLAIN (COSTS OFF)
SELECT COALESCE(SUM(l.quantity * l.sold_price), 0) AS total_sales,
       COUNT(DISTINCT t.order_id)                  AS order_count
FROM public.order_ticket t
JOIN public.order_line_item l ON l.order_id = t.order_id
WHERE t.order_timestamp >= CURRENT_DATE
  AND t.order_timestamp <  CURRENT_DATE + 1;
