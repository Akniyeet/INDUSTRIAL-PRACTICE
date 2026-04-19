package com.webizon.room.api.dto;

/**
 * The four Centrifugo channels a room client must subscribe to, in
 * their canonical string form produced by
 * {@code ChannelNameFactory.sessionChannel}.
 *
 * <p>Returning them in the bootstrap payload saves the client from
 * having to reconstruct the channel names on its own — the layout
 * is governed by {@code ChannelNameFactory} and should only ever
 * be generated on the server.
 */
public record RoomChannelBundleView(
        String chat,
        String cta,
        String presence,
        String state
) {
}
