-- ============================================================================
-- BREWPOS - 02_seed_reference_data.sql
-- OPTIONAL demo data (staff, categories, menu items). Idempotent: safe to
-- re-run; existing rows are left untouched. Contains NO orders.
-- Only needed when the database is empty and demo data is wanted.
-- ============================================================================

-- Staff (no staff-name uniqueness exists, so guard with NOT EXISTS)
INSERT INTO public.staff (full_name, role)
SELECT v.full_name, v.role
FROM (VALUES
    ('Demo Cashier', 'CASHIER'),
    ('Demo Manager', 'MANAGER')
) AS v(full_name, role)
WHERE NOT EXISTS (
    SELECT 1 FROM public.staff s WHERE s.full_name = v.full_name AND s.role = v.role
);

-- Categories (category_name is UNIQUE)
INSERT INTO public.menu_category (category_name)
VALUES ('Coffee'), ('Non-Coffee'), ('Pastries')
ON CONFLICT (category_name) DO NOTHING;

-- Menu items ((category_id, item_name) is UNIQUE)
INSERT INTO public.menu_item (category_id, item_name, price, is_active)
SELECT c.category_id, v.item_name, v.price, v.is_active
FROM (VALUES
    ('Coffee',     'Americano',         90.00, TRUE),
    ('Coffee',     'Cafe Latte',       120.00, TRUE),
    ('Coffee',     'Cappuccino',       120.00, TRUE),
    ('Coffee',     'Espresso',          80.00, TRUE),
    ('Non-Coffee', 'Iced Chocolate',   110.00, TRUE),
    ('Non-Coffee', 'Matcha Latte',     130.00, TRUE),
    ('Non-Coffee', 'Fruit Tea',        100.00, TRUE),
    ('Pastries',   'Butter Croissant',  75.50, TRUE),
    ('Pastries',   'Blueberry Muffin',  85.00, TRUE),
    ('Pastries',   'Chocolate Cookie',  60.00, TRUE),
    -- inactive on purpose: must NOT appear on the cashier menu or be orderable
    ('Coffee',     'Pumpkin Spice Latte', 140.00, FALSE)
) AS v(category_name, item_name, price, is_active)
JOIN public.menu_category c ON c.category_name = v.category_name
ON CONFLICT (category_id, item_name) DO NOTHING;

