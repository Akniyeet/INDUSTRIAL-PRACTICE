package com.webizon.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webizon.ai.config.AnthropicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin HTTP client for the Anthropic Messages API (v1).
 *
 * <p>Sends a structured prompt to Claude and returns the text response.
 * Handles serialisation, error logging, and token usage extraction.
 * Intentionally stateless — one call per scoring request.
 *
 * <p>If the API key is not configured, all calls return empty.
 */
@Component
@Slf4j
public class AnthropicClient {

    private final RestClient restClient;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    public AnthropicClient(
            @Qualifier("anthropicRestClient") RestClient restClient,
            AnthropicProperties props,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public record TokenUsage(int inputTokens, int outputTokens) {}
    public record AiResponse(String text, TokenUsage usage, String model) {}

    /**
     * Send a message to Claude and get a text response.
     *
     * @param systemPrompt the system-level instruction
     * @param userMessage  the user-level message with data
     * @return the AI response, or empty if API is not configured or call fails
     */
    public Optional<AiResponse> chat(String systemPrompt, String userMessage) {
        if (!props.isConfigured()) {
            log.debug("Anthropic API key not configured — skipping AI call");
            return Optional.empty();
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "max_tokens", props.getMaxTokens(),
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            String responseBody = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);

            // Extract text from content[0].text
            String text = "";
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                text = content.get(0).path("text").asText("");
            }

            // Extract usage
            JsonNode usage = root.path("usage");
            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            String model = root.path("model").asText(props.getModel());

            log.debug("Anthropic response: {} input tokens, {} output tokens, model={}",
                    inputTokens, outputTokens, model);

            return Optional.of(new AiResponse(text, new TokenUsage(inputTokens, outputTokens), model));

        } catch (Exception ex) {
            log.warn("Anthropic API call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
