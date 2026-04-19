package com.webizon.tenancy.model;

import com.webizon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A paying customer of the Webizon platform.
 *
 * <p><strong>Global entity</strong> — this table is NOT tenant-scoped; it is
 * the root of the tenancy tree. No {@code @TenantId} annotation.
 *
 * <p>The {@code slug} doubles as the public subdomain
 * ({@code <slug>.webizon.kz}) and must match {@code ^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$}
 * (enforced at the DB level in {@code V001__core_tenancy.sql}).
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 64, unique = true)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "country_code", nullable = false, length = 2, columnDefinition = "CHAR(2)")
    @Builder.Default
    private String countryCode = "KZ";

    @Column(name = "default_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @Builder.Default
    private String defaultCurrency = "KZT";

    @Column(name = "default_locale", nullable = false, length = 8)
    @Builder.Default
    private String defaultLocale = "ru-KZ";

    @Column(name = "default_timezone", nullable = false, length = 64)
    @Builder.Default
    private String defaultTimezone = "Asia/Almaty";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TenantStatus status = TenantStatus.TRIAL;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;
}
