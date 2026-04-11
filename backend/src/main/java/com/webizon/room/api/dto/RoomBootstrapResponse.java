package com.webizon.room.api.dto;

import com.webizon.cta.api.dto.CtaResponse;
import com.webizon.events.api.dto.EventResponse;
import com.webizon.events.api.dto.SessionResponse;

import java.util.List;

/**
 * Single-payload bootstrap for the authenticated room experience.
 *
 * <p>A viewer opens the room page, and the frontend needs every
 * piece of state required to render a working room in one round
 * trip: event metadata, session metadata, chat settings, a seed of
 * recent chat history, the CTAs currently visible, the list of
 * timeline rows a late-joiner needs to catch up on (AUTO only),
 * presence count, the caller's capability flags, and the Centrifugo
 * channel names to subscribe to.
 *
 * <p>Getting the connect and subscribe tokens is still a second
 * call to {@code /api/v1/realtime/*} on purpose — tokens are
 * short-lived and the client refreshes them on reconnect, so they
 * do not belong in a long-cacheable bootstrap payload.
 *
 * <p>{@code currentOffsetSeconds} is the <em>room's</em> offset at
 * bootstrap time. For LIVE sessions it is the seconds since
 * {@code actualStartedAt}; for AUTO sessions it matches the replay
 * engine's cursor. Clients use it to seek the video player so late
 * joiners do not start from 00:00.
 */
public record RoomBootstrapResponse(
        EventResponse event,
        SessionResponse session,
        RoomChatSettingsView chatSettings,
        List<RoomChatMessageView> recentChat,
        List<CtaResponse> activeCtas,
        long presentNowCount,
        Integer currentOffsetSeconds,
        RoomChannelBundleView channels,
        RoomCapabilitiesView capabilities
) {
}
