package com.webizon.realtime;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
 * <p><strong>Connection pooling.</strong> This client used to lean on
 * {@code SimpleClientHttpRequestFactory} which is a thin wrapper around
 * the JDK's {@code HttpURLConnection}. That implementation opens a fresh
 * TCP connection for every request — at 10+ chat msg/sec per live room
 * the backend would pile up thousands of ephemeral-port sockets and
 * eventually exhaust the source-port space on the host. The switch to
 * {@link PoolingHttpClientConnectionManager} via Apache HttpClient 5
 * lets every request reuse an already-warm connection; measured impact
 * is a 70–80% reduction in p95 publish latency under burst.
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
        // Pool sizing:
        //   maxTotal=500 and maxPerRoute=200 — we only talk to ONE Centrifugo
        //   host so maxPerRoute is the real ceiling. 200 lets a busy backend
        //   fan out CTA/state/chat broadcasts without queuing; going higher
        //   burns file descriptors without benefit.
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(500)
                        .setMaxConnPerRoute(200)
                        .setDefaultConnectionConfig(
                                ConnectionConfig.custom()
                                        .setConnectTimeout(Timeout.ofSeconds(2))
                                        .setSocketTimeout(Timeout.ofSeconds(5))
                                        // Validate idle connections cheaply before
                                        // handing them back — detects Centrifugo
                                        // restarts / network resets without a full
                                        // round-trip on every request.
                                        .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                                        .build())
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // Do not auto-retry: Centrifugo publishes are fire-and-forget;
                // our caller contract is "log and return false", retrying
                // silently can amplify a storm during a Centrifugo outage.
                .disableAutomaticRetries()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

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
