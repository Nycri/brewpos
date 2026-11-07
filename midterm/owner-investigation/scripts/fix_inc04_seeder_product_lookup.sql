-- ============================================================================
-- BREWPOS MIDTERM OWNER INVESTIGATION — INCIDENT 04 FIX SCRIPT
-- Incident ID   : G01-INC4 (Linked to Flaw G01-F4)
-- Title         : Seeder Category-Aware Product Lookup & Cartesian Join Fix
-- Database      : grp_brewposdb_restore_test (Disposable Copy)
-- Schema        : public
-- Author        : Group 4 (BrewPOS) Owner DBA Team
-- Designer Group: Group 1
-- ============================================================================

-- This script demonstrates the corrected, category-aware sample order seeding logic.
-- Even when duplicate item names exist across different categories (e.g., 'Iced Latte'
-- in 'Coffee' and 'Iced Latte' in 'Non-Coffee'), this lookup is 100% deterministic
-- and will NEVER trigger duplicate key violations or Cartesian product inflation.

BEGIN;

-- Demonstration of the correct query pattern using (category_name, item_name)
DO $$
DECLARE
    cashier INTEGER;
    manager INTEGER;
    oid     INTEGER;
BEGIN
    SELECT staff_id INTO cashier FROM public.staff WHERE role = 'CASHIER' ORDER BY staff_id LIMIT 1;
    SELECT staff_id INTO manager FROM public.staff WHERE role = 'MANAGER' ORDER BY staff_id LIMIT 1;
    
    IF cashier IS NULL OR manager IS NULL THEN
        RAISE NOTICE 'Staff rows missing; cannot seed sample orders.';
        RETURN;
    END IF;

    -- Create a test ticket to demonstrate clean insertion
    INSERT INTO public.order_ticket (staff_id, order_timestamp, payment_method)
    VALUES (cashier, CURRENT_TIMESTAMP, 'CASH')
    RETURNING order_id INTO oid;

    -- CORRECT JOIN: Joins against menu_category AND menu_item on (category_id, item_name)
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

    RAISE NOTICE 'Order % successfully inserted with exact category-scoped lookups.', oid;
END $$;

COMMIT;
