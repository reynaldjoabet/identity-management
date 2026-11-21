-- =====================================================================
-- Routine maintenance for the operational store
-- Target: PostgreSQL 19
--
-- !! DO NOT WRAP THIS FILE IN A TRANSACTION !!
--
-- Run it as:      psql -f maintenance.sql
-- NOT as:         psql --single-transaction -f maintenance.sql
--
-- REPACK (CONCURRENTLY) and ANALYZE both refuse to run inside a
-- transaction block. There is deliberately no START TRANSACTION here,
-- and the cleanup below is batched so that no single statement holds a
-- long transaction open anyway.
--
-- Requires the MAINTAIN privilege on the target tables.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Expire dead rows
--
-- Deleted in bounded batches rather than one large DELETE. A single
-- statement covering millions of rows holds one long transaction, pins
-- the tables' dead tuples against vacuum for its whole duration, and
-- bloats WAL; a loop of small commits lets autovacuum keep pace.
-- ---------------------------------------------------------------------
DO $cleanup$
DECLARE
    batch     constant integer := 10000;
    removed   bigint;
    total     bigint := 0;
BEGIN
    -- Device codes: expiration is NOT NULL, so every row is a candidate.
    LOOP
        DELETE FROM idmgmt.device_codes
        WHERE user_code IN (
            SELECT user_code FROM idmgmt.device_codes
            WHERE expiration < now()
            ORDER BY expiration
            LIMIT batch
        );
        GET DIAGNOSTICS removed = ROW_COUNT;
        total := total + removed;
        EXIT WHEN removed = 0;
    END LOOP;
    RAISE NOTICE 'device_codes: % expired rows removed', total;

    -- Pushed authorization requests: single-use and very short-lived.
    total := 0;
    LOOP
        DELETE FROM idmgmt.pushed_authorization_requests
        WHERE id IN (
            SELECT id FROM idmgmt.pushed_authorization_requests
            WHERE expires_at_utc < now()
            ORDER BY expires_at_utc
            LIMIT batch
        );
        GET DIAGNOSTICS removed = ROW_COUNT;
        total := total + removed;
        EXIT WHEN removed = 0;
    END LOOP;
    RAISE NOTICE 'pushed_authorization_requests: % expired rows removed', total;

    -- Persisted grants: expired, or consumed long enough ago that no
    -- replay-detection window still needs them. A grant with a NULL
    -- expiration never expires and is left alone.
    total := 0;
    LOOP
        DELETE FROM idmgmt.persisted_grants
        WHERE id IN (
            SELECT id FROM idmgmt.persisted_grants
            WHERE (expiration IS NOT NULL AND expiration < now())
               OR (consumed_time IS NOT NULL AND consumed_time < now() - interval '30 days')
            LIMIT batch
        );
        GET DIAGNOSTICS removed = ROW_COUNT;
        total := total + removed;
        EXIT WHEN removed = 0;
    END LOOP;
    RAISE NOTICE 'persisted_grants: % expired/consumed rows removed', total;

    -- Server-side sessions.
    total := 0;
    LOOP
        DELETE FROM idmgmt.server_side_sessions
        WHERE id IN (
            SELECT id FROM idmgmt.server_side_sessions
            WHERE expires IS NOT NULL AND expires < now()
            ORDER BY expires
            LIMIT batch
        );
        GET DIAGNOSTICS removed = ROW_COUNT;
        total := total + removed;
        EXIT WHEN removed = 0;
    END LOOP;
    RAISE NOTICE 'server_side_sessions: % expired rows removed', total;
END
$cleanup$;

-- ---------------------------------------------------------------------
-- 2. Reclaim the space those deletes left behind
--
-- REPACK replaces VACUUM FULL and CLUSTER (both still accepted, for
-- compatibility). The CONCURRENTLY option is the reason to prefer it:
-- the bulk of the work runs under SHARE UPDATE EXCLUSIVE, so reads and
-- writes continue, and ACCESS EXCLUSIVE is taken only for the final
-- file swap. VACUUM FULL would have locked these tables outright for
-- the whole rebuild -- on a token store, that is an outage.
--
-- USING INDEX also physically orders each table by the index given,
-- so subsequent range scans over it read sequentially.
--
-- Caveats that apply here:
--   * Needs a spare replication slot; see max_repack_replication_slots
--     (default 5). Repacking more tables at once than slots available
--     will fail.
--   * Not MVCC-safe, and may abort if another session runs DDL against
--     the table while it works.
--   * Not valid for partitioned or UNLOGGED tables, and requires a
--     primary key or an index-based replica identity -- which is why
--     every table named below has one.
-- ---------------------------------------------------------------------

-- Ordered by expiration: the cleanup above scans this range every run.
REPACK (CONCURRENTLY, ANALYZE) idmgmt.device_codes
    USING INDEX ix_device_codes_expiration;

REPACK (CONCURRENTLY, ANALYZE) idmgmt.pushed_authorization_requests
    USING INDEX ix_pushed_authorization_requests_expires_at_utc;

-- Ordered by subject so that "every grant for this user", the shape
-- revocation and consent screens issue, reads adjacent heap pages.
REPACK (CONCURRENTLY, ANALYZE) idmgmt.persisted_grants
    USING INDEX ix_persisted_grants_subject_id_client_id_type;

REPACK (CONCURRENTLY, ANALYZE) idmgmt.server_side_sessions
    USING INDEX ix_server_side_sessions_subject_id;

-- ---------------------------------------------------------------------
-- 3. Observability
--
-- Run these while the above is in flight, from another session.
-- ---------------------------------------------------------------------

-- Live progress of any REPACK, CLUSTER or VACUUM FULL.
--   SELECT * FROM pg_stat_progress_repack;

-- How autovacuum is currently ranking these tables against each other.
-- Useful for checking whether the per-table settings in duende.sql are
-- actually moving the hot tables up the queue.
--   SELECT * FROM pg_stat_autovacuum_scores
--   WHERE relid = ANY (ARRAY['idmgmt.persisted_grants'::regclass,
--                            'idmgmt.server_side_sessions'::regclass]);

-- Remaining bloat and when each table was last cleaned.
SELECT relname,
       n_live_tup,
       n_dead_tup,
       CASE WHEN n_live_tup > 0
            THEN round(100.0 * n_dead_tup / n_live_tup, 1)
            ELSE 0 END AS dead_pct,
       last_vacuum,
       last_autovacuum,
       last_analyze
FROM pg_stat_user_tables
WHERE schemaname = 'idmgmt'
ORDER BY n_dead_tup DESC;
