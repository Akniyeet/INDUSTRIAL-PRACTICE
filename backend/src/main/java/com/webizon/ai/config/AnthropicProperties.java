package com.webizon.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Anthropic (Claude) API.
 *
 * <p>Reads from {@code webizon.anthropic.*} in application.yml.
 * The API key is required for AI lead scoring; if blank, the
 * scoring service gracefully degrades to rule-based only.
 */
@Component
@ConfigurationProperties(prefix = "webizon.anthropic")
@Getter
@Setter
public class AnthropicProperties {

    private String apiKey = "";
    private String model = "claude-sonnet-4-6-20250514";
    private int maxTokens = 512;
    private String baseUrl = "https://api.anthropic.com";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
