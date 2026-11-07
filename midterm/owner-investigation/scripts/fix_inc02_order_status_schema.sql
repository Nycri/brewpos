-- ============================================================================
-- BREWPOS MIDTERM OWNER INVESTIGATION — INCIDENT 02 FIX SCRIPT
-- Incident ID   : G01-INC2 (Linked to Flaw G01-F2)
-- Title         : Explicit Order Status Lifecycle Management Schema Fix
-- Database      : grp_brewposdb_restore_test (Disposable Copy)
-- Schema        : public
-- Author        : Group 4 (BrewPOS) Owner DBA Team
-- Designer Group: Group 1
-- ============================================================================

BEGIN;

-- 1. Add order_status column with default 'COMPLETED' for historical rows
ALTER TABLE public.order_ticket
    ADD COLUMN IF NOT EXISTS order_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

-- 2. Add CHECK constraint enforcing valid POS lifecycle transitions
ALTER TABLE public.order_ticket
    DROP CONSTRAINT IF EXISTS chk_order_ticket_status;

ALTER TABLE public.order_ticket
    ADD CONSTRAINT chk_order_ticket_status
    CHECK (order_status IN ('PENDING', 'PREPARING', 'COMPLETED', 'CANCELLED'));

-- 3. Reset default to 'PENDING' for newly created tickets
ALTER TABLE public.order_ticket
    ALTER COLUMN order_status SET DEFAULT 'PENDING';

-- 4. Create index to optimize kitchen display and queue filtering queries
CREATE INDEX IF NOT EXISTS idx_order_ticket_status 
    ON public.order_ticket (order_status);

-- 5. Create composite index for daily sales aggregations excluding cancelled orders
CREATE INDEX IF NOT EXISTS idx_order_ticket_status_timestamp 
    ON public.order_ticket (order_status, order_timestamp);

COMMIT;

ANALYZE public.order_ticket;
