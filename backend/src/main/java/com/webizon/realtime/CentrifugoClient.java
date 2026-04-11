package com.webizon.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Thin HTTP client for Centrifugo's server API.
 *
 * <p>Publishes are fire-and-forget from the caller's perspective; failures are
 * logged and enqueued to a retry topic by {@code CentrifugoRetryPublisher}
 * (added in Phase 3).
 *
 * <p><strong>Do not construct raw channel names here.</strong> Callers must pass
 * channel names built by {@link ChannelNameFactory}.
 */
@Component
@Slf4j
public class CentrifugoClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public CentrifugoClient(
            @Value("${webizon.centrifugo.url}") String centrifugoUrl,
            @Value("${webizon.centrifugo.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(centrifugoUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", apiKey)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    {
                        setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
                        setReadTimeout((int) Duration.ofSeconds(5).toMillis());
                    }
                })
                .build();
    }

    /**
     * Publish a message to a channel. Returns true on success.
     * Failures are logged; callers should not block on the return value in hot paths.
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
        } catch (Exception ex) {
            log.error("Failed to publish to Centrifugo channel '{}': {}", channel, ex.getMessage());
            return false;
        }
    }

    /**
     * Broadcast the same payload to multiple channels in one API call.
     * Useful for fan-out across tenant-scoped channels.
     */
    public boolean broadcast(java.util.List<String> channels, Object payload) {
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
        } catch (Exception ex) {
            log.error("Failed to broadcast to Centrifugo channels {}: {}", channels, ex.getMessage());
            return false;
        }
    }
}
