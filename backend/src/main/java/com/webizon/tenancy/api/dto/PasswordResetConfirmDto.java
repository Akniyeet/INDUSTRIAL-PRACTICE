package com.webizon.tenancy.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
        @Email @NotBlank String email,
        @NotBlank String code,
        @NotBlank @Size(min = 8, message = "Пароль должен быть не менее 8 символов") String newPassword
) {}
