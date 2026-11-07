-- ============================================================================
-- BREWPOS MIDTERM OWNER INVESTIGATION — INCIDENT 03 FIX SCRIPT
-- Incident ID   : G01-INC3 (Linked to Flaw G01-F3)
-- Title         : Alignment of Database DDL with Documented Security Architecture
-- Database      : grp_brewposdb_restore_test (Disposable Copy)
-- Schema        : public
-- Author        : Group 4 (BrewPOS) Owner DBA Team
-- Designer Group: Group 1
-- ============================================================================

BEGIN;

-- 1. Create documented group roles (NOLOGIN)
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
END $$;

-- 2. Database and Schema Grants
GRANT CONNECT ON DATABASE grp_brewposdb TO app_runtime_grp, app_readonly_grp, app_admin_grp;
GRANT USAGE ON SCHEMA public TO app_runtime_grp, app_readonly_grp, app_admin_grp;
REVOKE CREATE ON SCHEMA public FROM app_runtime_grp, app_readonly_grp, app_admin_grp;

-- 3. Reset existing grants on public tables/sequences for group roles
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM app_runtime_grp, app_readonly_grp, app_admin_grp;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM app_runtime_grp, app_readonly_grp, app_admin_grp;

-- 4. Apply documented permissions to app_runtime_grp (Spring Boot Application)
GRANT SELECT ON public.staff TO app_runtime_grp;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.menu_category, public.menu_item TO app_runtime_grp;
GRANT SELECT, INSERT ON public.order_ticket, public.order_line_item TO app_runtime_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_runtime_grp;

-- 5. Apply documented permissions to app_readonly_grp (Auditor / Finance)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly_grp;

-- 6. Apply documented permissions to app_admin_grp (Elevated Catalog Management)
GRANT SELECT ON public.staff TO app_admin_grp;
GRANT ALL PRIVILEGES ON public.menu_category, public.menu_item TO app_admin_grp;
GRANT SELECT ON public.order_ticket, public.order_line_item TO app_admin_grp;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_admin_grp;

-- 7. Create documented login roles and establish group membership
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_app') THEN
        CREATE ROLE brewpos_app WITH
            LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'brewpos_auditor') THEN
        CREATE ROLE brewpos_auditor WITH
            LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
    END IF;
END $$;

-- Enforce membership
GRANT app_runtime_grp TO brewpos_app;
GRANT app_readonly_grp TO brewpos_auditor;

-- Hardening session limits on login roles
ALTER ROLE brewpos_app IN DATABASE grp_brewposdb SET search_path = public;
ALTER ROLE brewpos_app IN DATABASE grp_brewposdb SET statement_timeout = '30s';
ALTER ROLE brewpos_app IN DATABASE grp_brewposdb SET lock_timeout = '10s';

ALTER ROLE brewpos_auditor IN DATABASE grp_brewposdb SET search_path = public;
ALTER ROLE brewpos_auditor IN DATABASE grp_brewposdb SET statement_timeout = '60s';
ALTER ROLE brewpos_auditor IN DATABASE grp_brewposdb SET default_transaction_read_only = on;

COMMIT;
