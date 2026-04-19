package com.webizon.timeline.model;

/**
 * Every kind of action the timeline engine can schedule.
 *
 * <p>The payload for each action type is stored in the row's
 * {@code payload_json} column and its shape is enforced by the
 * application layer — the DB only stores raw JSONB. Documented
 * schemas per type:
 *
 * <ul>
 *   <li>{@link #CTA_SHOW}: {@code {"ctaId": "<uuid>"}} — render the CTA
 *       and register a CTA_IMPRESSION analytics event.</li>
 *   <li>{@link #CTA_HIDE}: {@code {"ctaId": "<uuid>"}} — remove the CTA
 *       from its placement.</li>
 *   <li>{@link #ADMIN_MESSAGE_SHOW}: {@code {"text": "<string>"}} —
 *       post a moderator-authored message as an ADMIN chat row.</li>
 *   <li>{@link #SYSTEM_MESSAGE_SHOW}: {@code {"text": "<string>"}} —
 *       post an automated SYSTEM chat row (welcome, cooldown notice).</li>
 *   <li>{@link #HISTORICAL_CHAT_REPLAY}: {@code {"messageId": "<uuid>"}}
 *       — emit a historical chat row from the source live session.
 *       Present in case we want selective replays; the default replay
 *       path iterates chat_messages by offset directly without
 *       generating per-message timeline rows.</li>
 *   <li>{@link #ROOM_STATE_CHANGE}: {@code {"state": "<string>"}} —
 *       force a client-side room state transition (rare; reserved for
 *       intermission-style breaks).</li>
 *   <li>{@link #FUTURE_RESERVED} — sentinel for schema evolution;
 *       service layer treats unknown payloads as no-ops so an old
 *       client never crashes on a new row.</li>
 * </ul>
 */
public enum TimelineActionType {
    CTA_SHOW,
    CTA_HIDE,
    ADMIN_MESSAGE_SHOW,
    SYSTEM_MESSAGE_SHOW,
    HISTORICAL_CHAT_REPLAY,
    ROOM_STATE_CHANGE,
    FUTURE_RESERVED
}
