package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Hard cap on message length.
 *
 * <p>The DB column is {@code VARCHAR(2000)} but we stop at 500 chars
 * here so the UI never has to render a 2KB wall-of-text and so one
 * user cannot monopolise the live feed. 500 is generous for a live
 * chat sentence, tight enough to preserve readability on mobile.
 *
 * <p>Also rejects blank / whitespace-only messages; that is a UI
 * bug-prevention measure — if the front-end sends {@code "   "} after
 * a copy-paste, the user should see an error rather than a ghost row
 * appearing in chat.
 *
 * <p>Runs very early in the chain (but after ban/mute) so we fail
 * cheap on obviously invalid input before touching settings or link
 * regex.
 */
@Component
@Order(5)
public class LengthPolicy implements ChatPolicy {

    private static final int MAX_LENGTH = 500;

    @Override
    public void check(ChatPolicyContext ctx) {
        String text = ctx.text();
        if (text == null || text.isBlank()) {
            throw new ChatPolicyViolation("empty", "Message cannot be empty.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new ChatPolicyViolation(
                    "too-long",
                    "Message is too long (max " + MAX_LENGTH + " characters)."
            );
        }
    }
}
