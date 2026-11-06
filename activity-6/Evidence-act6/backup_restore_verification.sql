-- =============================================================================
-- COMP 018: ACTIVITY 6 STANDARDIZED POST-RESTORE VERIFICATION SUITE
-- Auditing Group: Group 4 (BrewPOS)
-- Database Engine: PostgreSQL 18+ (Compatible with v16+)
-- =============================================================================

-- 1. Verify Restored Database Context & Table Inventory
SELECT current_database() AS restored_database_name, current_user AS connected_user;

SELECT 
    table_name,
    table_type
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 2. Verify Table Ownership
SELECT 
    schemaname, tablename, tableowner 
FROM pg_tables 
WHERE schemaname = 'public' 
ORDER BY tablename;

-- 3. Verify Constraints (Primary Keys, Foreign Keys, Checks, Uniques)
SELECT 
    tc.table_name, 
    tc.constraint_name, 
    tc.constraint_type
FROM information_schema.table_constraints tc
WHERE tc.table_schema = 'public'
ORDER BY tc.table_name, tc.constraint_type;

-- 4. Verify Activity 4 Roles Persistence
SELECT 
    r.rolname AS role_name,
    r.rolcanlogin AS can_login,
    r.rolsuper AS is_superuser
FROM pg_roles r
ORDER BY r.rolname;

-- 5. Row Parity Verification Template
-- (Run COUNT(*) queries on all public tables to compare against baseline counts)