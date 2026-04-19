-- V016 :: Landing page configuration stored as JSONB.
-- Contains benefits + timeline blocks for the public event landing page.
-- Flexible structure — no separate tables needed.

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS landing_config JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN events.landing_config IS
    'Landing page builder data: {"benefitsTitle":"...","benefits":[...],"timelineTitle":"...","timeline":[...]}';
