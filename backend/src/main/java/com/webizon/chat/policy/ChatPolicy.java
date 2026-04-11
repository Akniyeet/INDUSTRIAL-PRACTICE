package com.webizon.chat.policy;

/**
 * One rule in the chat policy chain.
 *
 * <p>Policies run in the order returned by Spring's
 * {@link org.springframework.core.annotation.Order} / {@code @Order}
 * annotation on each bean — the chain is assembled by injecting a
 * {@code List<ChatPolicy>}, which Spring sorts automatically.
 *
 * <p>Each policy either returns normally (pass) or throws
 * {@link ChatPolicyViolation} with a stable code and a human message.
 * Policies MUST NOT mutate the {@link ChatPolicyContext} — it is a
 * record and all fields are immutable, so this is enforced by the type
 * system.
 */
public interface ChatPolicy {

    /**
     * Evaluate this rule against the candidate message.
     *
     * @throws ChatPolicyViolation if the message breaks this policy;
     *         the chain stops on the first violation
     */
    void check(ChatPolicyContext ctx);
}
