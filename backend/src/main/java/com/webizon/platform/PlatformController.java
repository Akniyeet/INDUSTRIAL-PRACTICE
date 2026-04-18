package com.webizon.platform;

import com.webizon.billing.config.BillingProperties;
import com.webizon.platform.dto.PlatformDashboardResponse;
import com.webizon.platform.dto.PlatformRevenueResponse;
import com.webizon.platform.dto.PlatformTenantResponse;
import com.webizon.platform.dto.PlatformUserResponse;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.model.MembershipRole;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.TenantRepository;
import com.webizon.tenancy.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Platform-level super-admin API.
 *
 * <p>All endpoints here are gated behind {@code @platformAdminGuard.check()} which
 * verifies {@code users.is_platform_admin = true} for the caller. The {@link AllowCrossTenant}
 * annotation prevents the tenant entity listener from rejecting cross-tenant reads.
 *
 * <p>Revenue figures are stored as BIGINT KZT-millis in the DB and are converted
 * to human-readable {@link BigDecimal} KZT (2 decimal places) before being sent
 * to the frontend.
 */
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@AllowCrossTenant(reason = "Platform admin reads data across all tenants")
public class PlatformController {

    private static final BigDecimal MILLIS_DIVISOR = new BigDecimal("1000");

    private final TenantRepository     tenantRepository;
    private final UserRepository        userRepository;
    private final JdbcTemplate          jdbc;
    private final BillingProperties     billingProperties;

    // -------------------------------------------------------------------------
    // Dashboard
    // -------------------------------------------------------------------------

    @GetMapping("/dashboard")
    @PreAuthorize("@platformAdminGuard.check()")
    public PlatformDashboardResponse dashboard() {
        // Tenant counts
        long totalTenants  = jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class);
        long activeTenants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE status IN ('ACTIVE','TRIAL')", Long.class);

        // User count
        long totalUsers = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        // Event / session counts (cross-tenant — views bypass RLS)
        long totalEvents    = queryLong("SELECT COALESCE(SUM(total_events),0)    FROM v_platform_events_per_tenant");
        long totalSessions  = queryLong("SELECT COALESCE(SUM(total_sessions),0)  FROM v_platform_sessions_per_tenant");
        long currentlyLive  = queryLong("SELECT COALESCE(SUM(currently_live),0)  FROM v_platform_sessions_per_tenant");

        // Revenue all-time
        long allTimeTotal = queryLong(
                "SELECT COALESCE(SUM(total_paid_millis),0) FROM v_platform_revenue_per_tenant");
        long allTimeVat   = queryLong(
                "SELECT COALESCE(SUM(vat_paid_millis),0)   FROM v_platform_revenue_per_tenant");

        // Revenue this month
        long thisMonthTotal = queryLong("""
                SELECT COALESCE(SUM(total_millis),0) FROM billing_invoices
                WHERE status = 'PAID'
                  AND date_trunc('month', period_start) = date_trunc('month', now())
                """);
        long thisMonthVat   = queryLong("""
                SELECT COALESCE(SUM(vat_millis),0) FROM billing_invoices
                WHERE status = 'PAID'
                  AND date_trunc('month', period_start) = date_trunc('month', now())
                """);

        // Monthly trend
        List<PlatformDashboardResponse.MonthlyRevenue> monthly = jdbc.query("""
                SELECT to_char(month,'YYYY-MM') AS month_str,
                       total_millis, vat_millis, paying_tenants
                FROM   v_platform_monthly_revenue
                ORDER  BY month DESC
                LIMIT  12
                """,
                (rs, i) -> new PlatformDashboardResponse.MonthlyRevenue(
                        rs.getString("month_str"),
                        millisToKzt(rs.getLong("total_millis")),
                        millisToKzt(rs.getLong("vat_millis")),
                        rs.getLong("paying_tenants")
                )
        );

        return new PlatformDashboardResponse(
                totalTenants,
                activeTenants,
                totalUsers,
                totalEvents,
                totalSessions,
                currentlyLive,
                millisToKzt(thisMonthTotal - thisMonthVat),
                millisToKzt(thisMonthVat),
                millisToKzt(allTimeTotal - allTimeVat),
                monthly
        );
    }

    // -------------------------------------------------------------------------
    // Tenants
    // -------------------------------------------------------------------------

    @GetMapping("/tenants")
    @PreAuthorize("@platformAdminGuard.check()")
    public Page<PlatformTenantResponse> listTenants(Pageable pageable) {
        Page<Tenant> tenants = tenantRepository.findAll(pageable);

        // Bulk-load aggregate stats
        Map<UUID, long[]> eventStats   = loadMapLongArray(
                "SELECT tenant_id, total_events, published_events FROM v_platform_events_per_tenant");
        Map<UUID, long[]> sessionStats = loadMapLongArray(
                "SELECT tenant_id, total_sessions, currently_live FROM v_platform_sessions_per_tenant");
        Map<UUID, long[]> revStats     = loadMapLongArray(
                "SELECT tenant_id, total_paid_millis, total_outstanding_millis FROM v_platform_revenue_per_tenant");
        Map<UUID, String[]> ownerInfo  = loadOwnerInfo();
        Map<UUID, Long> memberCounts   = loadMemberCounts();

        List<PlatformTenantResponse> rows = tenants.getContent().stream().map(t -> {
            long[] ev  = eventStats.getOrDefault(t.getId(), new long[]{0,0});
            long[] ss  = sessionStats.getOrDefault(t.getId(), new long[]{0,0});
            long[] rev = revStats.getOrDefault(t.getId(), new long[]{0,0});
            String[] owner = ownerInfo.getOrDefault(t.getId(), new String[]{"", ""});
            long members = memberCounts.getOrDefault(t.getId(), 0L);

            return new PlatformTenantResponse(
                    t.getId(), t.getSlug(), t.getDisplayName(),
                    t.getStatus().name(), t.getTrialEndsAt(), t.getCreatedAt(),
                    owner[0], owner[1],
                    members, ev[0], ss[0], ss[1],
                    millisToKzt(rev[0]), millisToKzt(rev[1]),
                    null
            );
        }).toList();

        return new PageImpl<>(rows, pageable, tenants.getTotalElements());
    }

    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("@platformAdminGuard.check()")
    public PlatformTenantResponse getTenant(@PathVariable UUID tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId));

        long[] ev  = loadOneLong2(
                "SELECT total_events, published_events FROM v_platform_events_per_tenant WHERE tenant_id = ?", tenantId);
        long[] ss  = loadOneLong2(
                "SELECT total_sessions, currently_live FROM v_platform_sessions_per_tenant WHERE tenant_id = ?", tenantId);
        long[] rev = loadOneLong2(
                "SELECT total_paid_millis, total_outstanding_millis FROM v_platform_revenue_per_tenant WHERE tenant_id = ?", tenantId);
        String[] owner = loadOwnerInfo().getOrDefault(tenantId, new String[]{"", ""});
        long members = loadMemberCounts().getOrDefault(tenantId, 0L);

        return new PlatformTenantResponse(
                t.getId(), t.getSlug(), t.getDisplayName(),
                t.getStatus().name(), t.getTrialEndsAt(), t.getCreatedAt(),
                owner[0], owner[1],
                members, ev[0], ss[0], ss[1],
                millisToKzt(rev[0]), millisToKzt(rev[1]), null
        );
    }

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------

    @GetMapping("/users")
    @PreAuthorize("@platformAdminGuard.check()")
    public Page<PlatformUserResponse> listUsers(
            @RequestParam(required = false) String search,
            Pageable pageable) {

        List<User> users = search != null && !search.isBlank()
                ? userRepository.searchByNameOrEmail("%" + search.toLowerCase() + "%", pageable.getPageSize())
                : userRepository.findAll(pageable).getContent();

        long total = search != null && !search.isBlank()
                ? users.size()
                : userRepository.count();

        Map<UUID, List<String>> slugMap = loadUserTenantSlugs(
                users.stream().map(User::getId).collect(Collectors.toList()));

        List<PlatformUserResponse> rows = users.stream()
                .map(u -> PlatformUserResponse.from(u, slugMap.getOrDefault(u.getId(), List.of())))
                .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    // -------------------------------------------------------------------------
    // Revenue
    // -------------------------------------------------------------------------

    @GetMapping("/revenue")
    @PreAuthorize("@platformAdminGuard.check()")
    public PlatformRevenueResponse revenue() {
        // All-time totals
        long[] allTime = loadOneLong3("""
                SELECT COALESCE(SUM(subtotal_paid_millis),0),
                       COALESCE(SUM(vat_paid_millis),0),
                       COALESCE(SUM(total_paid_millis),0)
                FROM   v_platform_revenue_per_tenant
                """);
        long outstanding = queryLong(
                "SELECT COALESCE(SUM(total_outstanding_millis),0) FROM v_platform_revenue_per_tenant");

        // This month
        long[] thisMonth = loadOneLong3("""
                SELECT COALESCE(SUM(subtotal_millis) FILTER (WHERE status='PAID'),0),
                       COALESCE(SUM(vat_millis)      FILTER (WHERE status='PAID'),0),
                       COALESCE(SUM(total_millis)    FILTER (WHERE status='PAID'),0)
                FROM   billing_invoices
                WHERE  date_trunc('month', period_start) = date_trunc('month', now())
                """);

        // Top 10 tenants by revenue
        List<PlatformRevenueResponse.TenantRevenueRow> topTenants = jdbc.query("""
                SELECT r.tenant_id, t.slug, t.display_name,
                       r.total_paid_millis, r.vat_paid_millis,
                       r.invoice_count
                FROM   v_platform_revenue_per_tenant r
                JOIN   tenants t ON t.id = r.tenant_id
                ORDER  BY r.total_paid_millis DESC
                LIMIT  10
                """,
                (rs, i) -> new PlatformRevenueResponse.TenantRevenueRow(
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("slug"),
                        rs.getString("display_name"),
                        millisToKzt(rs.getLong("total_paid_millis")),
                        millisToKzt(rs.getLong("vat_paid_millis")),
                        rs.getLong("invoice_count")
                )
        );

        // Top 10 events by revenue
        List<PlatformRevenueResponse.EventRevenueRow> topEvents = jdbc.query("""
                SELECT r.event_id, r.tenant_id, t.slug AS tenant_slug,
                       e.title AS event_title,
                       r.total_amount_millis, r.session_count
                FROM   v_platform_revenue_per_event r
                JOIN   tenants t ON t.id = r.tenant_id
                LEFT   JOIN events e ON e.id = r.event_id
                ORDER  BY r.total_amount_millis DESC
                LIMIT  10
                """,
                (rs, i) -> new PlatformRevenueResponse.EventRevenueRow(
                        (UUID) rs.getObject("event_id"),
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("tenant_slug"),
                        rs.getString("event_title"),
                        millisToKzt(rs.getLong("total_amount_millis")),
                        rs.getLong("session_count")
                )
        );

        // Monthly trend
        List<PlatformRevenueResponse.MonthlyRow> monthly = jdbc.query("""
                SELECT to_char(month,'YYYY-MM') AS month_str,
                       subtotal_millis, vat_millis, total_millis, paying_tenants
                FROM   v_platform_monthly_revenue
                ORDER  BY month DESC
                """,
                (rs, i) -> new PlatformRevenueResponse.MonthlyRow(
                        rs.getString("month_str"),
                        millisToKzt(rs.getLong("subtotal_millis")),
                        millisToKzt(rs.getLong("vat_millis")),
                        millisToKzt(rs.getLong("total_millis")),
                        rs.getLong("paying_tenants")
                )
        );

        return new PlatformRevenueResponse(
                millisToKzt(allTime[0]), millisToKzt(allTime[1]), millisToKzt(allTime[2]),
                millisToKzt(thisMonth[0]), millisToKzt(thisMonth[1]), millisToKzt(thisMonth[2]),
                millisToKzt(outstanding),
                topTenants, topEvents, monthly
        );
    }

    // -------------------------------------------------------------------------
    // Impersonation — returns tenant context the frontend can pass as X-Tenant-Id
    // -------------------------------------------------------------------------

    @PostMapping("/tenants/{tenantId}/impersonate")
    @PreAuthorize("@platformAdminGuard.check()")
    public Map<String, Object> impersonate(@PathVariable UUID tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId));
        return Map.of(
                "tenantId",   t.getId().toString(),
                "tenantSlug", t.getSlug(),
                "displayName", t.getDisplayName()
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private long queryLong(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private static BigDecimal millisToKzt(long millis) {
        return new BigDecimal(millis).divide(MILLIS_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    /** Returns a map: tenant_id → long[2] from a query with columns (tenant_id, col1, col2). */
    private Map<UUID, long[]> loadMapLongArray(String sql) {
        return jdbc.query(sql, rs -> {
            Map<UUID, long[]> m = new HashMap<>();
            while (rs.next()) {
                UUID id = (UUID) rs.getObject(1);
                m.put(id, new long[]{ rs.getLong(2), rs.getLong(3) });
            }
            return m;
        });
    }

    private long[] loadOneLong2(String sql, Object... args) {
        return jdbc.query(sql, rs -> {
            if (rs.next()) return new long[]{ rs.getLong(1), rs.getLong(2) };
            return new long[]{ 0L, 0L };
        }, args);
    }

    private long[] loadOneLong3(String sql, Object... args) {
        return jdbc.query(sql, rs -> {
            if (rs.next()) return new long[]{ rs.getLong(1), rs.getLong(2), rs.getLong(3) };
            return new long[]{ 0L, 0L, 0L };
        }, args);
    }

    /** Maps: tenant_id → [ownerEmail, ownerFullName] */
    private Map<UUID, String[]> loadOwnerInfo() {
        return jdbc.query("""
                SELECT tu.tenant_id, u.email, u.full_name
                FROM   tenant_users tu
                JOIN   users u ON u.id = tu.user_id
                WHERE  tu.role = 'TENANT_OWNER'
                  AND  tu.status = 'ACTIVE'
                """, rs -> {
            Map<UUID, String[]> m = new HashMap<>();
            while (rs.next()) {
                m.put((UUID) rs.getObject("tenant_id"),
                        new String[]{ rs.getString("email"), rs.getString("full_name") });
            }
            return m;
        });
    }

    /** Maps: tenant_id → member count */
    private Map<UUID, Long> loadMemberCounts() {
        return jdbc.query("""
                SELECT tenant_id, COUNT(*) AS cnt
                FROM   tenant_users
                WHERE  status = 'ACTIVE'
                GROUP  BY tenant_id
                """, rs -> {
            Map<UUID, Long> m = new HashMap<>();
            while (rs.next()) m.put((UUID) rs.getObject("tenant_id"), rs.getLong("cnt"));
            return m;
        });
    }

    /** Maps: user_id → list of tenant slugs */
    private Map<UUID, List<String>> loadUserTenantSlugs(List<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<UUID, List<String>> m = new HashMap<>();
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.query(
                "SELECT tu.user_id, t.slug FROM tenant_users tu JOIN tenants t ON t.id = tu.tenant_id " +
                "WHERE tu.user_id IN (" + placeholders + ")",
                userIds.toArray(),
                (rs, i) -> {
                    UUID uid = (UUID) rs.getObject("user_id");
                    m.computeIfAbsent(uid, k -> new ArrayList<>()).add(rs.getString("slug"));
                    return null;
                }
        );
        return m;
    }
}
