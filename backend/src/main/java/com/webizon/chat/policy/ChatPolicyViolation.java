package com.webizon.chat.policy;

/**
 * Thrown by a {@link ChatPolicy} when a message fails a rule.
 *
 * <p>The {@link #code()} is a stable string that the front-end can
 * switch on to render a localised error (e.g. {@code "slow-mode"},
 * {@code "muted"}, {@code "links-disabled"}). The {@link #getMessage()}
 * is a human-readable fallback for logs and English UI.
 *
 * <p>Policy violations are expected, not exceptional — the chat hot path
 * will see dozens per second during a heated session. We still model
 * them as exceptions because it keeps the chain's return type clean
 * ({@code void check(...)}) and lets the enclosing service treat the
 * first failure as the canonical rejection without cascading checks.
 */
public class ChatPolicyViolation extends RuntimeException {

    private final String code;

    public ChatPolicyViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
