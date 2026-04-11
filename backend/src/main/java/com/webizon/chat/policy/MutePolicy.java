package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rejects messages from users whose mute is still in effect.
 *
 * <p>Mute resolution is delegated to
 * {@link com.webizon.chat.model.ChatUserStatus#isEffectivelyMuted}, which
 * collapses the two encodings (indefinite flag and timed expiry) into a
 * single predicate. Expired mutes are not cleared here — the
 * enforcement point is the only place that needs an authoritative
 * answer, and lazy-clearing avoids a background job.
 *
 * <p>Moderators bypass mute — in practice only for the pathological
 * case where a moderator has been muted by another moderator; Webizon
 * does not enforce strict rank ordering yet.
 */
@Component
@Order(20)
public class MutePolicy implements ChatPolicy {

    @Override
    public void check(ChatPolicyContext ctx) {
        if (ctx.isModerator()) {
            return;
        }
        var status = ctx.userStatus();
        if (status == null) {
            return;
        }
        if (status.isEffectivelyMuted(ctx.now())) {
            throw new ChatPolicyViolation("muted", "You are muted in this session.");
        }
    }
}
