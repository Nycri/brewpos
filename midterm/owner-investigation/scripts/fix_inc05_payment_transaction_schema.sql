-- ============================================================================
-- BREWPOS MIDTERM OWNER INVESTIGATION — INCIDENT 05 FIX SCRIPT
-- Incident ID   : G01-INC5 (Linked to Flaw G01-F5)
-- Title         : Dedicated Payment Transaction Table Architecture & Migration
-- Database      : grp_brewposdb_restore_test (Disposable Copy)
-- Schema        : public
-- Author        : Group 4 (BrewPOS) Owner DBA Team
-- Designer Group: Group 1
-- ============================================================================

BEGIN;

-- 1. Create the dedicated payment_transaction table
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

-- 2. Performance indexes for audit and order lookups
CREATE INDEX IF NOT EXISTS idx_payment_order_id 
    ON public.payment_transaction (order_id);

CREATE INDEX IF NOT EXISTS idx_payment_timestamp 
    ON public.payment_transaction (payment_timestamp);

CREATE INDEX IF NOT EXISTS idx_payment_status_method 
    ON public.payment_transaction (payment_status, payment_method);

-- 3. Idempotent Data Migration: Populate payment_transaction from historical order_ticket rows
-- Calculates total price from order_line_item (SUM(quantity * sold_price))
INSERT INTO public.payment_transaction (
    order_id, 
    payment_method, 
    amount, 
    tendered_amount, 
    change_amount, 
    transaction_ref, 
    payment_status, 
    payment_timestamp
)
SELECT 
    ot.order_id,
    CASE 
        WHEN ot.payment_method = 'ONLINE' THEN 'ONLINE'
        ELSE 'CASH'
    END AS payment_method,
    COALESCE(SUM(oli.quantity * oli.sold_price), 0.00) AS amount,
    COALESCE(SUM(oli.quantity * oli.sold_price), 0.00) AS tendered_amount,
    0.00 AS change_amount,
    'MIGRATED-ORDER-' || ot.order_id AS transaction_ref,
    'COMPLETED' AS payment_status,
    ot.order_timestamp
FROM public.order_ticket ot
LEFT JOIN public.order_line_item oli ON ot.order_id = oli.order_id
WHERE NOT EXISTS (
    SELECT 1 FROM public.payment_transaction pt WHERE pt.order_id = ot.order_id
)
GROUP BY ot.order_id, ot.payment_method, ot.order_timestamp
HAVING COALESCE(SUM(oli.quantity * oli.sold_price), 0.00) > 0;

-- 4. Grant appropriate privileges to application and auditor roles
GRANT SELECT, INSERT ON public.payment_transaction TO app_runtime_grp;
GRANT SELECT ON public.payment_transaction TO app_readonly_grp;
GRANT USAGE, SELECT ON SEQUENCE public.payment_transaction_payment_id_seq TO app_runtime_grp;

COMMIT;

ANALYZE public.payment_transaction;
