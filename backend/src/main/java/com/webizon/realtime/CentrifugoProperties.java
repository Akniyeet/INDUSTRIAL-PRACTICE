package com.webizon.realtime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime configuration for the Centrifugo v5 real-time transport.
 *
 * <p>All values are sourced from {@code webizon.centrifugo.*} (see
 * {@code application.yml}). In production every one of these MUST be an
 * environment variable override — the defaults in the YAML are for local
 * development only and are intentionally marked {@code dev_..._change_me}.
 *
 * @param url            base URL of the Centrifugo server; the backend uses
 *                       {@code ${url}/api/publish} etc. for server-side ops
 * @param apiKey         shared secret for Centrifugo's {@code /api} endpoints
 *                       (sent as the {@code X-API-Key} header)
 * @param tokenHmacSecret HMAC-SHA256 secret used to sign connect/subscribe
 *                       JWTs that clients present to Centrifugo
 * @param tokenTtlSeconds how long issued tokens remain valid (short because
 *                       clients renew on reconnect)
 */
@ConfigurationProperties(prefix = "webizon.centrifugo")
@Validated
public record CentrifugoProperties(
        @NotBlank String url,
        @NotBlank String apiKey,
        @NotBlank String tokenHmacSecret,
        @Min(30) long tokenTtlSeconds
) {}
