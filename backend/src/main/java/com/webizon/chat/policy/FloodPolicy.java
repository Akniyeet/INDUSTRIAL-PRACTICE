package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Minimum-gap anti-flood guard.
 *
 * <p>Independent of the configurable {@code slowModeSeconds}, this
 * policy enforces a hard 1-second floor between messages from the
 * same user <em>when anti-spam is enabled</em>. That catches two
 * common patterns the slow-mode UI does not:
 *
 * <ol>
 *   <li>Rapid double-submit from impatient clicks on mobile (users hit
 *       "send" twice while the first request is still in flight).</li>
 *   <li>Scripted flood attempts where the client tries to exploit a
 *       zero-valued {@code slowModeSeconds} to spam the feed.</li>
 * </ol>
 *
 * <p>Runs after {@link SlowModePolicy} so that in the common "slow mode
 * on" configuration the slow-mode check short-circuits this one,
 * meaning no extra cost for the common case.
 *
 * <p>Moderators bypass — a moderator sending back-to-back admin
 * messages is a real use case (e.g. posting a CTA followed by an
 * explanation).
 */
@Component
@Order(60)
public class FloodPolicy implements ChatPolicy {

    /** Absolute minimum gap between any two messages from the same user. */
    private static final Duration MIN_GAP = Duration.ofSeconds(1);

    @Override
    public void check(ChatPolicyContext ctx) {
        if (ctx.isModerator()) {
            return;
        }
        if (!ctx.settings().isAntiSpamEnabled()) {
            return;
        }
        var status = ctx.userStatus();
        if (status == null || status.getLastMessageAt() == null) {
            return;
        }
        Duration since = Duration.between(status.getLastMessageAt(), ctx.now());
        if (since.compareTo(MIN_GAP) < 0) {
            throw new ChatPolicyViolation(
                    "flood",
                    "You are sending messages too quickly."
            );
        }
    }
}
