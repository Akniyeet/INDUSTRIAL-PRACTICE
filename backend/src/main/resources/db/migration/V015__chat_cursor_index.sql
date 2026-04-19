-- V015 :: Composite index for keyset (cursor-based) chat pagination.
--
-- The live feed query orders by (created_at DESC, id DESC) and filters
-- on session_id + is_deleted + is_hidden. This index covers the full
-- query so PostgreSQL does an index-only scan instead of a heap fetch.
--
-- At 60K messages in a single session, offset-based pagination at
-- offset=50000 would scan 50K index entries. Keyset pagination with
-- this index seeks directly to the cursor row — O(log N) instead of
-- O(offset).

CREATE INDEX IF NOT EXISTS chat_messages_cursor_idx
    ON chat_messages (session_id, created_at DESC, id DESC)
    WHERE is_deleted = FALSE AND is_hidden = FALSE;
