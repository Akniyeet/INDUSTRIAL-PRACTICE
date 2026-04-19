-- V017 :: Additional chat settings fields for the event wizard.

ALTER TABLE event_chat_settings
    ADD COLUMN IF NOT EXISTS forbidden_words TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS block_phone_numbers BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS show_participant_count BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS show_participant_names BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS chat_enabled BOOLEAN NOT NULL DEFAULT true;
