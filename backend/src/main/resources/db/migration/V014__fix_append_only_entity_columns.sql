-- V014 :: Add updated_at + version to append-only tables whose JPA entities
--         extend BaseEntity (which declares those fields).
--
-- Both lead_signals and moderation_actions are conceptually append-only
-- (rows are never updated), but their entity classes extend BaseEntity
-- which maps updated_at and version.  We add the columns here so that
-- Hibernate schema-validation passes.  The set_updated_at trigger keeps
-- updated_at in sync on the rare case a row is ever touched.

ALTER TABLE lead_signals
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS version     BIGINT       NOT NULL DEFAULT 0;

CREATE TRIGGER lead_signals_updated_at
    BEFORE UPDATE ON lead_signals
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


ALTER TABLE moderation_actions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS version     BIGINT       NOT NULL DEFAULT 0;

CREATE TRIGGER moderation_actions_updated_at
    BEFORE UPDATE ON moderation_actions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
