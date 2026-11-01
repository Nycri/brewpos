-- ============================================================================
-- BREWPOS - 04_sample_orders.sql
-- OPTIONAL demo orders for testing history, details and reports.
-- Run AFTER 02_seed_reference_data.sql. Inserts nothing if order_ticket already
-- contains rows, so it can never pile onto or disturb real data.
-- sold_price is copied from the menu price at insert time; totals are NOT stored.
-- Timestamps are local time: today and yesterday, so daily reports show data.
-- ============================================================================
DO $$
DECLARE
    cashier   INTEGER;
    manager   INTEGER;
    oid       INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM public.order_ticket) THEN
        RAISE NOTICE 'order_ticket is not empty; sample orders skipped.';
        RETURN;
    END IF;

    SELECT staff_id INTO cashier FROM public.staff WHERE role = 'CASHIER' ORDER BY staff_id LIMIT 1;
    SELECT staff_id INTO manager FROM public.staff WHERE role = 'MANAGER' ORDER BY staff_id LIMIT 1;
    IF cashier IS NULL OR manager IS NULL OR NOT EXISTS (SELECT 1 FROM public.menu_item) THEN
        RAISE NOTICE 'Run 02_seed_reference_data.sql first; sample orders skipped.';
        RETURN;
    END IF;

    -- Order 1: today, CASH, 2 x Americano + 1 x Butter Croissant
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_DATE + TIME '09:15', 'CASH') RETURNING order_id INTO oid;
    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, item_id, q, price FROM (VALUES ('Americano', 2), ('Butter Croissant', 1)) v(n, q)
    JOIN public.menu_item m ON m.item_name = v.n;

    -- Order 2: today, ONLINE, 1 x Matcha Latte + 2 x Blueberry Muffin
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_DATE + TIME '10:40', 'ONLINE') RETURNING order_id INTO oid;
    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, item_id, q, price FROM (VALUES ('Matcha Latte', 1), ('Blueberry Muffin', 2)) v(n, q)
    JOIN public.menu_item m ON m.item_name = v.n;

    -- Order 3: today, CASH, 3 x Cafe Latte
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (manager, CURRENT_DATE + TIME '13:05', 'CASH') RETURNING order_id INTO oid;
    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, item_id, q, price FROM (VALUES ('Cafe Latte', 3)) v(n, q)
    JOIN public.menu_item m ON m.item_name = v.n;

    -- Order 4: yesterday, ONLINE, 1 x Cappuccino + 1 x Chocolate Cookie + 1 x Fruit Tea
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_DATE - 1 + TIME '16:30', 'ONLINE') RETURNING order_id INTO oid;
    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, item_id, q, price
    FROM (VALUES ('Cappuccino', 1), ('Chocolate Cookie', 1), ('Fruit Tea', 1)) v(n, q)
    JOIN public.menu_item m ON m.item_name = v.n;

    -- Order 5: yesterday, CASH, 2 x Espresso
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (manager, CURRENT_DATE - 1 + TIME '08:20', 'CASH') RETURNING order_id INTO oid;
    INSERT INTO public.order_line_item (order_id, item_id, quantity, sold_price)
    SELECT oid, item_id, q, price FROM (VALUES ('Espresso', 2)) v(n, q)
    JOIN public.menu_item m ON m.item_name = v.n;

    RAISE NOTICE 'Inserted 5 sample orders.';
END $$;

-- Expected totals (with the 02 seed prices):
--   today CASH   : (2*90 + 75.50) + (3*120)   = 615.50  (2 orders)
--   today ONLINE : (130 + 2*85)               = 300.00  (1 order)
--   today total  : 915.50 (3 orders)
--   yesterday    : ONLINE 280.00 (1) + CASH 160.00 (1) = 440.00 (2 orders)
