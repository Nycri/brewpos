-- =============================================================================
-- COMP 018: ACTIVITY 5 STANDARDIZED DIAGNOSTIC & MAINTENANCE SUITE
-- Auditing Group: Group 4 (BrewPOS)
-- Database Engine: PostgreSQL 18+ (Compatible with v16+)
-- =============================================================================

-- 1. Database Engine Version & Context
SELECT version() AS postgresql_version;
SELECT current_database() AS database_name, current_user AS connected_user;

-- 2. Total Database Size
SELECT pg_size_pretty(pg_database_size(current_database())) AS total_db_size;

-- 3. Table and Index Sizing Breakdown
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident(table_name))) AS index_size
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC;

-- 4. Connection & Transaction Health Metrics
SELECT 
    datname, 
    numbackends AS open_connections,
    xact_commit AS committed_transactions,
    xact_rollback AS rolled_back_transactions,
    conflicts,
    deadlocks
FROM pg_stat_database
WHERE datname = current_database();

-- 5. Active Sessions Inspection
SELECT 
    pid, usename, client_addr, application_name, state, 
    wait_event_type, wait_event, query_start, query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY query_start NULLS LAST;

-- 6. Table Statistics, Live/Dead Tuples & Bloat Audit
SELECT 
    relname AS table_name,
    seq_scan AS sequential_scans,
    seq_tup_read AS tuples_read_by_seq_scan,
    idx_scan AS index_scans,
    n_live_tup AS live_tuples,
    n_dead_tup AS dead_tuples,
    last_vacuum, last_autovacuum, last_analyze, last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;

-- 7. Routine Maintenance Execution (Execute as table owner or superuser)
ANALYZE;
-- Execute VACUUM (VERBOSE, ANALYZE) on tables accumulating dead tuples:
-- VACUUM (VERBOSE, ANALYZE) table_name;