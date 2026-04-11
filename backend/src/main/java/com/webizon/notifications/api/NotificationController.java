package com.webizon.notifications.api;

import com.webizon.notifications.api.dto.NotificationOutboxResponse;
import com.webizon.notifications.model.NotificationKind;
import com.webizon.notifications.model.NotificationOutboxEntry;
import com.webizon.notifications.model.NotificationStatus;
import com.webizon.notifications.repo.NotificationOutboxRepository;
import com.webizon.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Admin surface for the notification outbox.
 *
 * <p>This controller does NOT expose an enqueue endpoint. All
 * enqueues go through {@link NotificationService#enqueue} from
 * inside a business transaction so they ride on the same commit as
 * the triggering event. Exposing an HTTP enqueue would defeat the
 * outbox pattern's atomicity guarantee and is a deliberate
 * non-feature.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li>Paginated list with optional status / kind filter, for the
 *       admin "sent history" and "dead letter queue" views.</li>
 *   <li>Single-row detail including the full body text for
 *       post-incident debugging.</li>
 *   <li>Replay: flip a non-SENT row back to PENDING so the
 *       dispatcher picks it up again. Typical workflow: an outgoing
 *       SMTP is down for a day, rows hit DEAD, ops fixes SMTP and
 *       replays every DEAD row from the admin UI.</li>
 * </ul>
 *
 * <p>Access is restricted to TENANT_OWNER / TENANT_ADMIN — other
 * roles may include PII that should not be visible to moderators
 * or presenters.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public Page<NotificationOutboxResponse> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationKind kind,
            Pageable pageable) {

        Page<NotificationOutboxEntry> page;
        if (status != null && kind != null) {
            // Either filter alone is cheap; the double-filter case
            // is rare enough in practice that applying the second
            // filter in Java after a single-field DB query keeps
            // the repository surface small.
            page = outboxRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(e -> e); // materialise, then filter below via stream
            // Filter-in-memory fallback — acceptable for an admin UI
            // scoped to a single tenant's outbox.
            var filtered = page.getContent().stream()
                    .filter(e -> e.getKind() == kind)
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(
                    filtered.stream().map(NotificationOutboxResponse::summary).toList(),
                    pageable,
                    filtered.size());
        }
        if (status != null) {
            page = outboxRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (kind != null) {
            page = outboxRepository.findAllByKindOrderByCreatedAtDesc(kind, pageable);
        } else {
            page = outboxRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(NotificationOutboxResponse::summary);
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @GetMapping("/{entryId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public NotificationOutboxResponse get(@PathVariable UUID entryId) {
        NotificationOutboxEntry entry = outboxRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification outbox entry not found: " + entryId));
        return NotificationOutboxResponse.detail(entry);
    }

    // ------------------------------------------------------------------
    // Replay
    // ------------------------------------------------------------------

    @PostMapping("/{entryId}/replay")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public NotificationOutboxResponse replay(@PathVariable UUID entryId) {
        NotificationOutboxEntry replayed = notificationService.replay(entryId);
        return NotificationOutboxResponse.detail(replayed);
    }
}
