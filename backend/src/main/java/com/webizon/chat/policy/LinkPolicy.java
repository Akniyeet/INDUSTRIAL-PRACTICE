package com.webizon.chat.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Blocks URLs when the event has {@code allowLinks = false}.
 *
 * <p>The pattern is intentionally lenient — any occurrence of
 * {@code http://}, {@code https://}, or a bare domain-looking token
 * like {@code example.com} trips it. Over-blocking is acceptable for
 * MVP; tenants that want richer UX can flip {@code allowLinks} on.
 *
 * <p>Moderators bypass this check so they can pin legitimate resources
 * into chat.
 */
@Component
@Order(40)
public class LinkPolicy implements ChatPolicy {

    /**
     * Matches either a schemed URL ({@code https://…}) or a bare hostname
     * with a two-plus-letter TLD followed by a path or end of token.
     * Deliberately avoids full URL RFC compliance — we just need enough
     * signal to catch "come to spam.biz/promo" copy-paste.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(https?://\\S+|\\b[a-z0-9-]+\\.[a-z]{2,}(/\\S*)?)"
    );

    @Override
    public void check(ChatPolicyContext ctx) {
        if (ctx.isModerator()) {
            return;
        }
        if (ctx.settings().isAllowLinks()) {
            return;
        }
        if (URL_PATTERN.matcher(ctx.text()).find()) {
            throw new ChatPolicyViolation("links-disabled", "Links are not allowed in this session.");
        }
    }
}
