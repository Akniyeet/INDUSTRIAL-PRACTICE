package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal profanity filter.
 *
 * <p>This is intentionally a <em>floor</em> implementation — a small
 * static deny-list that catches the worst offenders. It is not meant
 * to be a comprehensive solution; a future migration will replace it
 * with a per-tenant list backed by a DB table and hot-reloadable
 * configuration. Keeping it simple for MVP lets us ship the chat path
 * without a moderation-content dependency.
 *
 * <p>Moderators bypass this — sometimes they need to quote a banned
 * word to explain why it is banned.
 */
@Component
@Order(50)
public class WordFilterPolicy implements ChatPolicy {

    /**
     * Minimal bilingual (ru/en) deny-list. Lowercased, matched as whole
     * words via {@link #WORD_BOUNDARY}. Extend with care — a false
     * positive in live chat is user-visible friction.
     */
    private static final Set<String> DENY_LIST = Set.of(
            "fuck", "shit", "bitch", "asshole",
            "хуй", "пизда", "ебан", "бля"
    );

    private static final Pattern WORD_BOUNDARY = Pattern.compile("[\\p{L}\\p{N}_]+");

    @Override
    public void check(ChatPolicyContext ctx) {
        if (ctx.isModerator()) {
            return;
        }
        if (!ctx.settings().isProfanityFilterEnabled()) {
            return;
        }
        String lower = ctx.text().toLowerCase(java.util.Locale.ROOT);
        var matcher = WORD_BOUNDARY.matcher(lower);
        while (matcher.find()) {
            String token = matcher.group();
            if (DENY_LIST.contains(token)) {
                throw new ChatPolicyViolation("profanity", "Please keep the chat respectful.");
            }
        }
    }
}
