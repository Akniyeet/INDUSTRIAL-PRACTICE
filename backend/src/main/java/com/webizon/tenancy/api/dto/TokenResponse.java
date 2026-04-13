package com.webizon.tenancy.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Normalized token envelope returned to the frontend after any auth action.
 * <p>Field names mirror Keycloak's token endpoint response so the frontend
 * can use them without a mapping layer.
 */
public record TokenResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in")    int expiresIn,
        @JsonProperty("token_type")    String tokenType
) {}
