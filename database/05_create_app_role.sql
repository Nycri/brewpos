-- ============================================================================
-- BREWPOS - 05_create_app_role.sql
-- Least-privilege, non-superuser PostgreSQL role for the Spring Boot backend.
--
-- Safe & idempotent: creates `brewpos_app` if missing, revokes broad privileges,
-- and grants only the exact table and sequence permissions required by BrewPOS.
-- Plaintext passwords are NEVER stored in SQL scripts or server logs.
--
-- 1) Run as PostgreSQL superuser / database owner connected to grp_brewposdb:
--      psql -U postgres -d grp_brewposdb -f database/05_create_app_role.sql
--
-- 2) Set the role password interactively (SCRAM-hashed client-side by psql):
--      psql -U postgres -d grp_brewposdb
--      \password brewpos_app
-- ============================================================================

-- 1. Create or verify non-superuser login role -------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_app') THEN
        CREATE ROLE brewpos_app WITH
            LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOREPLICATION;
        RAISE NOTICE 'Created role brewpos_app. Next, set its password in psql using: \password brewpos_app';
    ELSE
        ALTER ROLE brewpos_app WITH
            LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOREPLICATION;
        RAISE NOTICE 'Role brewpos_app already exists; non-superuser attributes verified.';
    END IF;
END $$;

-- 2. Harden session defaults for the application role ------------------------
-- Pin search_path to public (pg_catalog remains implicitly searched first) and
-- cap lock/statement waits so runaway queries cannot stall the database.
ALTER ROLE brewpos_app IN DATABASE grp_brewposdb SET search_path = public;
ALTER ROLE brewpos_app IN DATABASE grp_brewposdb SET statement_timeout = '30s';
ALTER ROLE brewpos_app IN DATABASE grp_brewposdb SET lock_timeout = '10s';

-- 3. Database & schema access (no DDL / CREATE table permission) -------------
GRANT CONNECT ON DATABASE grp_brewposdb TO brewpos_app;
GRANT USAGE ON SCHEMA public TO brewpos_app;
REVOKE CREATE ON SCHEMA public FROM brewpos_app;

-- Reset prior table/sequence grants so re-running enforces exact least privilege
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM brewpos_app;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM brewpos_app;

-- 4. Granular table privileges enforcing BrewPOS business rules --------------
-- staff: read-only for the application; staff records are DBA-managed only.
GRANT SELECT ON public.staff TO brewpos_app;

-- menu_category: CRUD allowed; FK fk_menu_item_category still blocks deleting
-- any category referenced by menu_item rows.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category TO brewpos_app;

-- menu_item: create/read/update/deactivate; DELETE is permitted only for
-- unreferenced test rows (e.g. OrderRollbackTests cleanup). Historical items
-- referenced by order_line_item CANNOT be deleted because fk_order_line_item
-- blocks parent deletion and brewpos_app has no DELETE on order_line_item.
-- (Optional strict lock-down when not running test cleanup:
--   REVOKE DELETE ON public.menu_item FROM brewpos_app;)
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_item TO brewpos_app;

-- order_ticket & order_line_item: append and read ONLY (no UPDATE, no DELETE).
-- Completed orders and historical sold_price values are immutable at the DB level.
GRANT SELECT, INSERT ON public.order_ticket TO brewpos_app;
GRANT SELECT, INSERT ON public.order_line_item TO brewpos_app;

-- 5. Sequence privileges for SERIAL / IDENTITY primary keys ------------------
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO brewpos_app;

