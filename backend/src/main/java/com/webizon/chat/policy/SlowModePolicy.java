package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Enforces {@link com.webizon.chat.model.EventChatSettings#getSlowModeSeconds()}.
 *
 * <p>If slow mode is off (0) or the caller is a moderator, the check is
 * a no-op. Otherwise the user must have waited at least
 * {@code slowModeSeconds} since their last message.
 *
 * <p>We compare against {@code last_message_at} on the user's status
 * row, not against a Redis cache, so the enforcement is durable across
 * backend restarts. The trade-off is one row update per message (done
 * by {@code ChatService} after the chain passes), which is cheap
 * compared to the network cost of the WebSocket broadcast that
 * follows.
 */
@Component
@Order(30)
public class SlowModePolicy implements ChatPolicy {

    @Override
    public void check(ChatPolicyContext ctx) {
        if (ctx.isModerator()) {
            return;
        }
        int slow = ctx.settings().getSlowModeSeconds();
        if (slow <= 0) {
            return;
        }
        var status = ctx.userStatus();
        if (status == null || status.getLastMessageAt() == null) {
            return;
        }
        Duration since = Duration.between(status.getLastMessageAt(), ctx.now());
        if (since.getSeconds() < slow) {
            long remaining = slow - since.getSeconds();
            throw new ChatPolicyViolation(
                    "slow-mode",
                    "Slow mode: wait " + remaining + " more seconds before sending another message."
            );
        }
    }
}
