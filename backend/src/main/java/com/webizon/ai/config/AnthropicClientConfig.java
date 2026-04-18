package com.webizon.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Creates a pre-configured {@link RestClient} bean for the Anthropic Messages API.
 */
@Configuration
@RequiredArgsConstructor
public class AnthropicClientConfig {

    private final AnthropicProperties props;

    @Bean("anthropicRestClient")
    public RestClient anthropicRestClient() {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("x-api-key", props.getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
