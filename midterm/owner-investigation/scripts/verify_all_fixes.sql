-- ============================================================================
-- BREWPOS MIDTERM OWNER INVESTIGATION — COMPREHENSIVE VERIFICATION SUITE
-- Tests all 5 Corrective Actions (G01-INC1 through G01-INC5)
-- Database : grp_brewposdb_restore_test
-- Schema   : public
-- Designer : Group 1 | Owner : Group 4 (BrewPOS)
-- ============================================================================

\echo '============================================================'
\echo 'TEST 1: Verifying G01-INC1 & G01-INC3 (RBAC Roles and Privileges)'
\echo '============================================================'

-- Verify group roles exist
SELECT rolname, rolcanlogin 
FROM pg_catalog.pg_roles 
WHERE rolname IN ('app_runtime_grp', 'app_readonly_grp', 'app_admin_grp', 'app_cashier_grp', 'brewpos_app', 'brewpos_auditor')
ORDER BY rolname;

-- Verify table privileges for app_runtime_grp and app_readonly_grp
SELECT grantee, table_name, privilege_type 
FROM information_schema.role_table_grants 
WHERE grantee IN ('app_runtime_grp', 'app_readonly_grp', 'app_cashier_grp')
ORDER BY grantee, table_name, privilege_type;

\echo '============================================================'
\echo 'TEST 2: Verifying G01-INC2 (Order Status Lifecycle)'
\echo '============================================================'

-- Positive Test: Insert valid statuses
DO $$
DECLARE
    cashier_id INT;
    o_pending INT;
    o_prep INT;
    o_done INT;
    o_canc INT;
BEGIN
    SELECT staff_id INTO cashier_id FROM public.staff LIMIT 1;
    
    INSERT INTO public.order_ticket (staff_id, order_status, payment_method)
    VALUES (cashier_id, 'PENDING', 'CASH') RETURNING order_id INTO o_pending;
    
    INSERT INTO public.order_ticket (staff_id, order_status, payment_method)
    VALUES (cashier_id, 'PREPARING', 'ONLINE') RETURNING order_id INTO o_prep;
    
    INSERT INTO public.order_ticket (staff_id, order_status, payment_method)
    VALUES (cashier_id, 'COMPLETED', 'CASH') RETURNING order_id INTO o_done;
    
    INSERT INTO public.order_ticket (staff_id, order_status, payment_method)
    VALUES (cashier_id, 'CANCELLED', 'CASH') RETURNING order_id INTO o_canc;
    
    RAISE NOTICE 'SUCCESS: Successfully inserted valid statuses (%, %, %, %)', o_pending, o_prep, o_done, o_canc;
END $$;

-- Negative Test: Invalid status should fail
DO $$
BEGIN
    INSERT INTO public.order_ticket (staff_id, order_status, payment_method)
    VALUES (1, 'SHIPPED', 'CASH');
    RAISE EXCEPTION 'TEST FAILED: Invalid status was accepted!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'PASSED EXPECTED FAILURE: Check violation triggered for invalid status (SHIPPED).';
END $$;

\echo '============================================================'
\echo 'TEST 3: Verifying G01-INC4 (Category-Aware Product Lookup)'
\echo '============================================================'

-- Setup homonymous items in different categories
DO $$
DECLARE
    cid_coffee INT;
    cid_noncoffee INT;
BEGIN
    SELECT category_id INTO cid_coffee FROM public.menu_category WHERE category_name = 'Coffee';
    SELECT category_id INTO cid_noncoffee FROM public.menu_category WHERE category_name = 'Non-Coffee';
    
    -- Insert 'Americano' under Non-Coffee to simulate duplicate name across categories
    INSERT INTO public.menu_item (category_id, item_name, price, is_active)
    VALUES (cid_noncoffee, 'Americano', 95.00, TRUE)
    ON CONFLICT (category_id, item_name) DO NOTHING;
END $$;

-- Verify query returns EXACTLY 1 row for Coffee -> Americano
SELECT m.item_id, c.category_name, m.item_name, m.price
FROM (VALUES ('Coffee', 'Americano')) AS v(cat_name, item_name)
JOIN public.menu_category c ON c.category_name = v.cat_name
JOIN public.menu_item m ON m.category_id = c.category_id AND m.item_name = v.item_name;

\echo '============================================================'
\echo 'TEST 4: Verifying G01-INC5 (Dedicated Payment Transaction Table)'
\echo '============================================================'

-- Positive Test: Insert valid payment transaction
DO $$
DECLARE
    target_oid INT;
    pid INT;
BEGIN
    SELECT order_id INTO target_oid FROM public.order_ticket ORDER BY order_id DESC LIMIT 1;
    
    INSERT INTO public.payment_transaction (
        order_id, payment_method, amount, tendered_amount, change_amount, transaction_ref, payment_status
    ) VALUES (
        target_oid, 'GCASH', 150.00, 150.00, 0.00, 'GCASH-TXN-984712', 'COMPLETED'
    ) RETURNING payment_id INTO pid;
    
    RAISE NOTICE 'SUCCESS: Inserted payment_transaction ID % for Order %', pid, target_oid;
END $$;

-- Negative Test: Negative amount must fail
DO $$
BEGIN
    INSERT INTO public.payment_transaction (
        order_id, payment_method, amount, payment_status
    ) VALUES (
        1, 'CASH', -50.00, 'COMPLETED'
    );
    RAISE EXCEPTION 'TEST FAILED: Negative amount was accepted!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'PASSED EXPECTED FAILURE: Check violation triggered for negative payment amount.';
END $$;

-- Negative Test: Invalid payment method must fail
DO $$
BEGIN
    INSERT INTO public.payment_transaction (
        order_id, payment_method, amount, payment_status
    ) VALUES (
        1, 'BITCOIN', 100.00, 'COMPLETED'
    );
    RAISE EXCEPTION 'TEST FAILED: Invalid payment method was accepted!';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'PASSED EXPECTED FAILURE: Check violation triggered for unsupported payment method.';
END $$;

\echo '============================================================'
\echo 'ALL VERIFICATION SUITE TESTS EXECUTED SUCCESSFULLY!'
\echo '============================================================'
