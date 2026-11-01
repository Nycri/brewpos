-- ============================================================================
-- BREWPOS — Consolidated Seeder & Sample SQL Data (database/seed.sql)
-- Target RDBMS : PostgreSQL 18.6
-- Database     : grp_brewposdb
-- Schema       : public
--
-- PURPOSE:
--   Populates initial reference records (staff, categories, active/inactive
--   menu items) AND sample historical/same-day orders so the entire BrewPOS
--   system (Register, Menu, Orders, and Daily Sales) can be tested immediately.
--
-- IDEMPOTENCY & SAFETY:
--   - Safe to re-run at any time.
--   - Staff, categories, and menu items use NOT EXISTS / ON CONFLICT DO NOTHING.
--   - Sample orders are inserted ONLY when public.order_ticket is empty, so
--     re-running never duplicates sample orders or disturbs live order data.
--
-- EXECUTION INSTRUCTIONS:
--   psql -U postgres -d grp_brewposdb -f database/seed.sql
-- ============================================================================

BEGIN;

-- 1. Seed Staff (1 Cashier, 1 Manager) ---------------------------------------
INSERT INTO public.staff (full_name, role)
SELECT v.full_name, v.role
FROM (VALUES
    ('Demo Cashier', 'CASHIER'),
    ('Demo Manager', 'MANAGER')
) AS v(full_name, role)
WHERE NOT EXISTS (
    SELECT 1
    FROM public.staff s
    WHERE s.full_name = v.full_name
      AND s.role = v.role
);

-- 2. Seed Menu Categories ----------------------------------------------------
INSERT INTO public.menu_category (category_name)
VALUES
    ('Coffee'),
    ('Non-Coffee'),
    ('Pastries')
ON CONFLICT (category_name) DO NOTHING;

-- 3. Seed Menu Items (10 Active + 1 Inactive to test POS filtering) ----------
INSERT INTO public.menu_item (category_id, item_name, price, is_active)
SELECT c.category_id, v.item_name, v.price, v.is_active
FROM (VALUES
    ('Coffee',     'Americano',           90.00, TRUE),
    ('Coffee',     'Cafe Latte',         120.00, TRUE),
    ('Coffee',     'Cappuccino',         120.00, TRUE),
    ('Coffee',     'Espresso',            80.00, TRUE),
    ('Non-Coffee', 'Iced Chocolate',     110.00, TRUE),
    ('Non-Coffee', 'Matcha Latte',       130.00, TRUE),
    ('Non-Coffee', 'Fruit Tea',          100.00, TRUE),
    ('Pastries',   'Butter Croissant',    75.50, TRUE),
    ('Pastries',   'Blueberry Muffin',    85.00, TRUE),
    ('Pastries',   'Chocolate Cookie',    60.00, TRUE),
    -- Intentionally inactive: hidden on Register, visible in Menu management
    ('Coffee',     'Pumpkin Spice Latte', 140.00, FALSE)
) AS v(category_name, item_name, price, is_active)
JOIN public.menu_category c ON c.category_name = v.category_name
ON CONFLICT (category_id, item_name) DO NOTHING;

-- 4. Seed Sample Orders (Today & Yesterday, only if order_ticket is empty) ---
DO $$
DECLARE
    cashier INTEGER;
    manager INTEGER;
    oid     INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM public.order_ticket) THEN
        RAISE NOTICE 'public.order_ticket already contains rows; skipping sample orders.';
        RETURN;
    END IF;

    SELECT staff_id INTO cashier FROM public.staff WHERE role = 'CASHIER' ORDER BY staff_id LIMIT 1;
    SELECT staff_id INTO manager FROM public.staff WHERE role = 'MANAGER' ORDER BY staff_id LIMIT 1;
    IF cashier IS NULL OR manager IS NULL THEN
        RAISE NOTICE 'A CASHIER and a MANAGER staff row are required; skipping sample orders.';
        RETURN;
    END IF;

    -- Order 1: Today 09:15, CASH — 2 x Americano (90.00) + 1 x Butter Croissant (75.50) = 255.50
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_DATE + TIME '09:15', 'CASH')
    RETURNING order_id INTO oid;

    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, m.item_id, v.qty, m.price
    FROM (VALUES ('Americano', 2), ('Butter Croissant', 1)) AS v(item_name, qty)
    JOIN public.menu_item m ON m.item_name = v.item_name;

    -- Order 2: Today 10:40, ONLINE — 1 x Matcha Latte (130.00) + 2 x Blueberry Muffin (85.00) = 300.00
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_DATE + TIME '10:40', 'ONLINE')
    RETURNING order_id INTO oid;

    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, m.item_id, v.qty, m.price
    FROM (VALUES ('Matcha Latte', 1), ('Blueberry Muffin', 2)) AS v(item_name, qty)
    JOIN public.menu_item m ON m.item_name = v.item_name;

    -- Order 3: Today 13:05, CASH — 3 x Cafe Latte (120.00) = 360.00
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (manager, CURRENT_DATE + TIME '13:05', 'CASH')
    RETURNING order_id INTO oid;

    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, m.item_id, v.qty, m.price
    FROM (VALUES ('Cafe Latte', 3)) AS v(item_name, qty)
    JOIN public.menu_item m ON m.item_name = v.item_name;

    -- Order 4: Yesterday 16:30, ONLINE — 1 x Cappuccino (120.00) + 1 x Chocolate Cookie (60.00) + 1 x Fruit Tea (100.00) = 280.00
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_DATE - 1 + TIME '16:30', 'ONLINE')
    RETURNING order_id INTO oid;

    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, m.item_id, v.qty, m.price
    FROM (VALUES ('Cappuccino', 1), ('Chocolate Cookie', 1), ('Fruit Tea', 1)) AS v(item_name, qty)
    JOIN public.menu_item m ON m.item_name = v.item_name;

    -- Order 5: Yesterday 08:20, CASH — 2 x Espresso (80.00) = 160.00
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (manager, CURRENT_DATE - 1 + TIME '08:20', 'CASH')
    RETURNING order_id INTO oid;

    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, m.item_id, v.qty, m.price
    FROM (VALUES ('Espresso', 2)) AS v(item_name, qty)
    JOIN public.menu_item m ON m.item_name = v.item_name;

    RAISE NOTICE 'Inserted 5 sample orders (Today total: 915.50 across 3 orders; Yesterday total: 440.00 across 2 orders).';
END $$;

COMMIT;

ANALYZE public.staff;
ANALYZE public.menu_category;
ANALYZE public.menu_item;
ANALYZE public.order_ticket;
ANALYZE public.order_line_item;

