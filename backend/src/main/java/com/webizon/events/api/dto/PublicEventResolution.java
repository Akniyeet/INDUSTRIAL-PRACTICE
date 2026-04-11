package com.webizon.events.api.dto;

import java.util.List;

/**
 * Response shape of the public landing page resolver:
 * {@code GET /api/v1/public/tenants/{tenantSlug}/events/{eventSlug}/resolve}.
 *
 * <p>The frontend uses {@link #state()} to decide which page to render:
 *
 * <ul>
 *   <li>{@link State#LIVE_NOW} — a session is currently airing; drop the
 *       user straight into the live room. {@link #activeSession()} is
 *       populated.</li>
 *   <li>{@link State#WAITING} — the next scheduled session is close enough
 *       to show the waiting room ("starts in 12 min"). {@link #nextSession()}
 *       is populated.</li>
 *   <li>{@link State#SLOT_SELECTION} — no airing session, but multiple
 *       upcoming AUTO replays exist. The user picks one.
 *       {@link #autoSlots()} is populated.</li>
 *   <li>{@link State#LANDING} — published event with no imminent session.
 *       Show the landing page.</li>
 *   <li>{@link State#UNAVAILABLE} — draft/archived event or no such slug.</li>
 * </ul>
 */
public record PublicEventResolution(
        State state,
        PublicEventView event,
        PublicSessionView activeSession,
        PublicSessionView nextSession,
        List<PublicSessionView> autoSlots
) {
    public enum State {
        LIVE_NOW,
        WAITING,
        SLOT_SELECTION,
        LANDING,
        UNAVAILABLE
    }

    public static PublicEventResolution unavailable() {
        return new PublicEventResolution(State.UNAVAILABLE, null, null, null, List.of());
    }

    public static PublicEventResolution landing(PublicEventView event) {
        return new PublicEventResolution(State.LANDING, event, null, null, List.of());
    }

    public static PublicEventResolution liveNow(PublicEventView event, PublicSessionView active) {
        return new PublicEventResolution(State.LIVE_NOW, event, active, null, List.of());
    }

    public static PublicEventResolution waiting(PublicEventView event, PublicSessionView next) {
        return new PublicEventResolution(State.WAITING, event, null, next, List.of());
    }

    public static PublicEventResolution slotSelection(PublicEventView event,
                                                      List<PublicSessionView> slots) {
        return new PublicEventResolution(State.SLOT_SELECTION, event, null, null, List.copyOf(slots));
    }
}
