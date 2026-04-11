package com.webizon.notifications.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Notification module configuration. Sourced from
 * {@code webizon.notifications.*} in {@code application.yml}.
 *
 * <p>The retry schedule is expressed as a list of {@link Duration}s
 * rather than a closed-form (base * multiplier^attempt) calculation
 * so ops can tune individual rungs of the ladder without touching
 * code. The dispatcher picks entry {@code attemptCount} from the
 * list; if {@code attemptCount >= backoff.size()} it reuses the
 * final entry as a ceiling — this avoids an out-of-bounds crash
 * when the max-attempts count is configured higher than the
 * backoff ladder length (which is always a misconfiguration, but a
 * misconfiguration should not crash the dispatcher).
 *
 * @param senderName    display name shown in the {@code From} header
 * @param senderAddress bare email address used as {@code From} and {@code Reply-To}
 * @param backoff       per-attempt delay ladder; index 0 is "after first failure"
 * @param dispatch      sweeper cadence and batch controls
 */
@ConfigurationProperties(prefix = "webizon.notifications")
@Validated
public record NotificationProperties(
        @NotBlank String senderName,
        @NotBlank @Email String senderAddress,
        @NotNull @Valid List<@NotNull Duration> backoff,
        @NotNull @Valid Dispatch dispatch
) {

    /**
     * Pick the delay that should precede the next retry after
     * {@code attemptCount} failures. Guaranteed to return a non-null
     * Duration even if the ladder is misconfigured.
     */
    public Duration backoffFor(int attemptCount) {
        if (backoff.isEmpty()) {
            return Duration.ofMinutes(5);
        }
        int idx = Math.min(attemptCount, backoff.size() - 1);
        return backoff.get(Math.max(0, idx));
    }

    public record Dispatch(
            /** How often the sweeper wakes. Matches the scheduler tick. */
            @NotNull Duration tickInterval,
            /** Initial delay on application boot. */
            @NotNull Duration initialDelay,
            /** Max rows processed per tenant per tick. */
            @Min(1) int batchSize,
            /**
             * Threshold past which a row stuck in SENDING is reclaimed
             * to PENDING. Must be large enough that a healthy send
             * never crosses it.
             */
            @NotNull Duration staleSendingTimeout
    ) {}
}
