package com.webizon.cta.service;

import com.webizon.cta.model.CtaPlacement;
import com.webizon.cta.model.EventCta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure function that turns a bag of active CTAs into the exact
 * per-placement visibility the client should render.
 *
 * <p>Without this resolver the rules from CLAUDE.md §57 would have to
 * live in the frontend, which means every client (web, future mobile,
 * embed) would have to reimplement them identically. Keeping the
 * resolution server-side guarantees one source of truth and keeps
 * AUTO replay deterministic (every slot for a given event produces
 * the exact same visible state at a given offset).
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Inactive CTAs are discarded.</li>
 *   <li>CTAs are grouped by placement.</li>
 *   <li>Within a placement, CTAs are ordered by priority descending
 *       (ties broken by creation order to keep output stable).</li>
 *   <li>If every CTA in the placement has {@code allowStack = true},
 *       all of them stay visible.</li>
 *   <li>Otherwise only the single highest-priority CTA is shown —
 *       even CTAs with {@code allowStack = true} are suppressed
 *       beneath a non-stacking one, because "non-stacking" is a
 *       declaration that the author wants exclusive rights to that
 *       placement.</li>
 *   <li>{@link CtaPlacement#POPUP} is always exclusive regardless
 *       of the {@code allowStack} flag — we never open two modals at
 *       once.</li>
 * </ol>
 */
@Component
public class CtaResolver {

    /**
     * Resolve visible CTAs for every placement on the page. The
     * returned map is keyed by placement and enumerates CTAs in
     * render order (highest priority first).
     *
     * <p>Placements with no visible CTA are omitted from the map, so
     * callers should treat a missing key as "nothing to render
     * here".
     */
    public Map<CtaPlacement, List<EventCta>> resolve(List<EventCta> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        Map<CtaPlacement, List<EventCta>> grouped = new EnumMap<>(CtaPlacement.class);
        for (EventCta cta : candidates) {
            if (!cta.isActive()) continue;
            grouped.computeIfAbsent(cta.getPlacement(), p -> new ArrayList<>()).add(cta);
        }

        Map<CtaPlacement, List<EventCta>> resolved = new EnumMap<>(CtaPlacement.class);
        for (var entry : grouped.entrySet()) {
            CtaPlacement placement = entry.getKey();
            List<EventCta> bucket = entry.getValue();
            bucket.sort(RENDER_ORDER);

            List<EventCta> visible = resolveBucket(placement, bucket);
            if (!visible.isEmpty()) {
                resolved.put(placement, visible);
            }
        }
        return resolved;
    }

    private List<EventCta> resolveBucket(CtaPlacement placement, List<EventCta> ordered) {
        // Popups never stack — always show the single highest.
        if (placement == CtaPlacement.POPUP) {
            return List.of(ordered.get(0));
        }
        // If every CTA in the bucket allows stacking, all of them are visible.
        boolean allStack = ordered.stream().allMatch(EventCta::isAllowStack);
        if (allStack) {
            return List.copyOf(ordered);
        }
        // Otherwise only the top priority wins — "non-stack" means exclusive.
        return List.of(ordered.get(0));
    }

    /**
     * Stable render order: priority DESC, then creation time ASC so
     * older CTAs at the same priority appear first. Creation time can
     * be null for freshly built-in-memory entities during tests; treat
     * those as "newest" to keep the comparator total.
     */
    private static final Comparator<EventCta> RENDER_ORDER = Comparator
            .comparingInt(EventCta::getPriority).reversed()
            .thenComparing((a, b) -> {
                var aT = a.getCreatedAt();
                var bT = b.getCreatedAt();
                if (aT == null && bT == null) return 0;
                if (aT == null) return 1;
                if (bT == null) return -1;
                return aT.compareTo(bT);
            });

    /** Set of placements that are always exclusive (useful for tests and debug). */
    public static final Set<CtaPlacement> EXCLUSIVE_PLACEMENTS = Set.of(CtaPlacement.POPUP);
}
