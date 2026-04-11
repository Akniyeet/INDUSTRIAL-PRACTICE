package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * First rule in the chain: bans are absolute.
 *
 * <p>A user who is chat-banned or room-banned cannot send messages for
 * the rest of the session, full stop. Moderators cannot bypass this
 * (in practice a moderator would never be banned, but the check is
 * symmetrical — we do not want a quirk where promoting a banned user
 * to moderator silently re-enables their chat).
 *
 * <p>Runs before {@link MutePolicy} so we do not waste time looking at
 * mute timestamps for users who cannot speak at all.
 */
@Component
@Order(10)
public class BanPolicy implements ChatPolicy {

    @Override
    public void check(ChatPolicyContext ctx) {
        var status = ctx.userStatus();
        if (status == null) {
            return;
        }
        if (status.isRoomBanned()) {
            throw new ChatPolicyViolation("room-banned", "You have been removed from this session.");
        }
        if (status.isChatBanned()) {
            throw new ChatPolicyViolation("chat-banned", "You cannot send messages in this session.");
        }
        if (!status.isCanSendMessages()) {
            throw new ChatPolicyViolation("chat-disabled", "Chat is disabled for your account.");
        }
    }
}
