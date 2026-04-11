package com.webizon.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for Centrifugo's server API.
 *
 * <p>Publishes are fire-and-forget from the caller's perspective: on failure
 * we log and return {@code false}. Callers on hot paths (chat, CTA sync)
 * MUST NOT block on the boolean return — they should hand the message off
 * to a retry buffer (Phase 4 work: {@code CentrifugoRetryPublisher}) and
 * let the websocket fall back to next reconnect.
 *
 * <p><strong>Do not construct raw channel names in callers.</strong> Channels
 * must come from {@link ChannelNameFactory} so that parsing and access
 * control stay consistent with publish.
 *
 * <p>Timeouts are deliberately short (2s connect, 5s read): Centrifugo runs
 * next to the backend and a slow response almost always means it is
 * overloaded — we would rather shed load than queue.
 */
@Component
@Slf4j
public class CentrifugoClient {

    private final RestClient restClient;

    public CentrifugoClient(CentrifugoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", properties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Publish a single message to one channel.
     *
     * @return {@code true} on 2xx, {@code false} on any error (logged)
     */
    public boolean publish(String channel, Object payload) {
        try {
            var body = Map.of(
                    "channel", channel,
                    "data", payload
            );
            var response = restClient.post()
                    .uri("/api/publish")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            log.error("Failed to publish to Centrifugo channel '{}': {}", channel, ex.getMessage());
            return false;
        }
    }

    /**
     * Broadcast the same payload to multiple channels in a single API call.
     *
     * <p>Useful when a server-side event (e.g. session state change) must
     * hit several channel kinds at once — STATE + CONTROL for instance.
     */
    public boolean broadcast(List<String> channels, Object payload) {
        if (channels == null || channels.isEmpty()) {
            return true;
        }
        try {
            var body = Map.of(
                    "channels", channels,
                    "data", payload
            );
            var response = restClient.post()
                    .uri("/api/broadcast")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            log.error("Failed to broadcast to Centrifugo channels {}: {}", channels, ex.getMessage());
            return false;
        }
    }
}
