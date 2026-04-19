package com.webizon.tenancy.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CodeExchangeRequest(
        @NotBlank String code,
        @NotBlank String redirectUri,
        @NotBlank String codeVerifier
) {}
