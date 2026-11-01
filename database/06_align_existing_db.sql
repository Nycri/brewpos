-- ============================================================================
-- BREWPOS - 06_align_existing_db.sql
-- Non-destructive DBA alignment script for an EXISTING `grp_brewposdb`.
--
-- Safe & idempotent (modifies ZERO table rows, adds ZERO tables or columns):
--   1. Replaces ON DELETE CASCADE on order_line_item -> order_ticket with
--      PostgreSQL's default NO ACTION (restrict-style historical preservation),
--      aligning pre-existing databases with 01_create_tables.sql & Section 4.4.
--   2. Ensures query-justified indexes exist (idx_order_ticket_timestamp and
--      idx_order_line_item_item_id).
--   3. Refreshes planner statistics via ANALYZE on all five tables.
--
-- Run as database owner / superuser:
--      psql -U postgres -d grp_brewposdb -f database/06_align_existing_db.sql
-- ============================================================================

BEGIN;

-- 1. Align order_line_item -> order_ticket FK (remove ON DELETE CASCADE) -----
DO $$
DECLARE
    v_conname TEXT;
    v_deltype "char";
    r         RECORD;
BEGIN
    IF to_regclass('public.staff') IS NULL
       OR to_regclass('public.menu_category') IS NULL
       OR to_regclass('public.menu_item') IS NULL
       OR to_regclass('public.order_ticket') IS NULL
       OR to_regclass('public.order_line_item') IS NULL THEN
        RAISE EXCEPTION 'One or more BrewPOS tables are missing in public. Use schema.sql for an empty database.';
    END IF;

    SELECT c.conname, c.confdeltype
      INTO v_conname, v_deltype
      FROM pg_catalog.pg_constraint c
     WHERE c.conrelid  = 'public.order_line_item'::regclass
       AND c.confrelid = 'public.order_ticket'::regclass
       AND c.contype   = 'f'
     LIMIT 1;

    IF v_conname IS NULL THEN
        ALTER TABLE public.order_line_item
            ADD CONSTRAINT fk_order_line_order
            FOREIGN KEY (order_id) REFERENCES public.order_ticket (order_id);
        RAISE NOTICE 'Added missing foreign key fk_order_line_order (NO ACTION / no cascade).';
    ELSIF v_deltype = 'c' THEN
        EXECUTE format('ALTER TABLE public.order_line_item DROP CONSTRAINT %I', v_conname);
        ALTER TABLE public.order_line_item
            ADD CONSTRAINT fk_order_line_order
            FOREIGN KEY (order_id) REFERENCES public.order_ticket (order_id);
        RAISE NOTICE 'Aligned FK % -> fk_order_line_order without ON DELETE CASCADE.', v_conname;
    ELSE
        RAISE NOTICE 'FK % already prevents cascade deletion (confdeltype=%). No change needed.', v_conname, v_deltype;
    END IF;

    -- Warn if any other foreign key in public still uses ON DELETE CASCADE
    FOR r IN
        SELECT conrelid::regclass AS child_tbl, conname
          FROM pg_catalog.pg_constraint
         WHERE connamespace = 'public'::regnamespace
           AND contype = 'f'
           AND confdeltype = 'c'
    LOOP
        RAISE WARNING 'Foreign key %.% still has ON DELETE CASCADE; review manually.', r.child_tbl, r.conname;
    END LOOP;
END $$;

-- 2. Ensure query-justified indexes exist ------------------------------------
CREATE INDEX IF NOT EXISTS idx_order_ticket_timestamp
    ON public.order_ticket (order_timestamp);

CREATE INDEX IF NOT EXISTS idx_order_line_item_item_id
    ON public.order_line_item (item_id);

COMMIT;

-- 3. Update planner statistics for accurate index selection ------------------
ANALYZE public.staff,
        public.menu_category,
        public.menu_item,
        public.order_ticket,
        public.order_line_item;

