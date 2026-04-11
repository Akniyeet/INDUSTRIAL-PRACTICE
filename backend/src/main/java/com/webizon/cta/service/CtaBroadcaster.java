package com.webizon.cta.service;

import com.webizon.cta.model.EventCta;
import com.webizon.events.model.Session;
import com.webizon.realtime.CentrifugoClient;
import com.webizon.realtime.ChannelKind;
import com.webizon.realtime.ChannelNameFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Centralises the envelope shape for CTA real-time events.
 *
 * <p>Splitting this out of {@link CtaService} means the timeline
 * replay path ({@code TimelineService}) can publish CTA pulses during
 * AUTO sessions without depending on {@code CtaService} — and
 * {@code CtaService} still calls this same broadcaster for LIVE
 * "show" / "hide" clicks, so both code paths produce byte-identical
 * envelopes. Any client logic that depends on the envelope shape only
 * needs to track one producer.
 *
 * <p>Publish failures are logged at WARN level but never propagated:
 * a failed broadcast is a transient delivery problem, not a domain
 * error, and the caller's transaction should commit regardless.
 * Clients recover from missed pulses by backfilling the current CTA
 * set through {@code GET /api/v1/events/{id}/ctas?active=true} on
 * reconnect.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CtaBroadcaster {

    private final CentrifugoClient centrifugoClient;
    private final ChannelNameFactory channelNameFactory;

    public void broadcastShow(Session session, EventCta cta, int offsetSeconds) {
        publish(session, Map.ofEntries(
                Map.entry("action", "CTA_SHOW"),
                Map.entry("ctaId", cta.getId().toString()),
                Map.entry("placement", cta.getPlacement().name()),
                Map.entry("priority", cta.getPriority()),
                Map.entry("allowStack", cta.isAllowStack()),
                Map.entry("title", cta.getTitle()),
                Map.entry("buttonText", cta.getButtonText()),
                Map.entry("type", cta.getType().name()),
                Map.entry("actionUrl", cta.getActionUrl() == null ? "" : cta.getActionUrl()),
                Map.entry("fileUrl", cta.getFileUrl() == null ? "" : cta.getFileUrl()),
                Map.entry("description", cta.getDescription() == null ? "" : cta.getDescription()),
                Map.entry("offsetSeconds", offsetSeconds)
        ));
    }

    public void broadcastHide(Session session, EventCta cta, int offsetSeconds) {
        publish(session, Map.of(
                "action", "CTA_HIDE",
                "ctaId", cta.getId().toString(),
                "placement", cta.getPlacement().name(),
                "offsetSeconds", offsetSeconds
        ));
    }

    private void publish(Session session, Map<String, Object> envelope) {
        String channel = channelNameFactory.sessionChannel(
                session.getTenantId(), session.getId(), ChannelKind.CTA);
        boolean ok = centrifugoClient.publish(channel, envelope);
        if (!ok) {
            log.warn("Centrifugo CTA publish failed on channel {} for session {}",
                    channel, session.getId());
        }
    }
}
