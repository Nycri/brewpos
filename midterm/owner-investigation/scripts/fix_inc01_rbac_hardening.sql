-- ============================================================================
-- BREWPOS MIDTERM OWNER INVESTIGATION — INCIDENT 01 FIX SCRIPT
-- Incident ID   : G01-INC1 (Linked to Flaw G01-F1)
-- Title         : Incomplete Role-Based Access Control (RBAC) Hardening
-- Database      : grp_brewposdb_restore_test (Disposable Copy)
-- Schema        : public
-- Author        : Group 4 (BrewPOS) Owner DBA Team
-- Designer Group: Group 1
-- ============================================================================

BEGIN;

-- 1. Ensure Group Roles Exist (NOLOGIN)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_runtime_grp') THEN
        CREATE ROLE app_runtime_grp WITH NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_readonly_grp') THEN
        CREATE ROLE app_readonly_grp WITH NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_admin_grp') THEN
        CREATE ROLE app_admin_grp WITH NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_cashier_grp') THEN
        CREATE ROLE app_cashier_grp WITH NOLOGIN;
    END IF;
END $$;

-- 2. Database & Schema Grants
GRANT CONNECT ON DATABASE grp_brewposdb TO app_runtime_grp, app_readonly_grp, app_admin_grp, app_cashier_grp;
GRANT USAGE ON SCHEMA public TO app_runtime_grp, app_readonly_grp, app_admin_grp, app_cashier_grp;

-- 3. Revoke dangerous broad DML on schema
REVOKE CREATE ON SCHEMA public FROM app_runtime_grp, app_readonly_grp, app_admin_grp, app_cashier_grp;

-- 4. Configure app_cashier_grp (PoLP: Register Operations ONLY)
-- Cashiers can ONLY read staff and active menu, and insert order tickets/lines.
-- Absolutely NO UPDATE/DELETE on catalog, staff, or sales tables!
GRANT SELECT ON public.staff TO app_cashier_grp;
GRANT SELECT ON public.menu_category, public.menu_item TO app_cashier_grp;
GRANT SELECT, INSERT ON public.order_ticket, public.order_line_item TO app_cashier_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_cashier_grp;

-- 5. Configure app_admin_grp (Store Manager / Catalog Administration)
GRANT SELECT ON public.staff TO app_admin_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category, public.menu_item TO app_admin_grp;
GRANT SELECT ON public.order_ticket, public.order_line_item TO app_admin_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_admin_grp;

-- 6. Configure app_readonly_grp (Auditor / Finance)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly_grp;

-- 7. Configure app_runtime_grp (Service Account Baseline)
GRANT app_cashier_grp TO app_runtime_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category, public.menu_item TO app_runtime_grp;

-- 8. Assign Login Roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_app') THEN
        CREATE ROLE brewpos_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_auditor') THEN
        CREATE ROLE brewpos_auditor WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_cashier') THEN
        CREATE ROLE brewpos_cashier WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_manager') THEN
        CREATE ROLE brewpos_manager WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END $$;

GRANT app_runtime_grp TO brewpos_app;
GRANT app_readonly_grp TO brewpos_auditor;
GRANT app_cashier_grp TO brewpos_cashier;
GRANT app_admin_grp TO brewpos_manager;

COMMIT;
