-- V013: Add chat_mode column to event_chat_settings.
-- Supports three modes: EVERYONE (default), ADMINS_ONLY, DISABLED.

ALTER TABLE event_chat_settings
    ADD COLUMN chat_mode VARCHAR(20) NOT NULL DEFAULT 'EVERYONE';

COMMENT ON COLUMN event_chat_settings.chat_mode
    IS 'EVERYONE = all authenticated users can write, ADMINS_ONLY = only moderators/admins, DISABLED = chat is read-only for everyone';
