package com.webizon.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-request tenant context stored in a {@link ThreadLocal}.
 *
 * <p>Set exclusively by {@link TenantContextFilter} for HTTP requests and by
 * Kafka listeners that process tenant-scoped events. Never set or cleared from
 * business logic.
 *
 * <p><strong>Safety note:</strong> virtual threads reuse the underlying carrier,
 * but each virtual thread has its own ThreadLocal map, so this remains safe.
 * However, every code path that spawns a new thread must propagate the context
 * explicitly (see {@link #copy()} and {@link #runWith(UUID, Runnable)}).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("utility class");
    }

    public static void set(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getRequired() {
        UUID tid = CURRENT_TENANT.get();
        if (tid == null) {
            throw new IllegalStateException(
                    "TenantContext is not set. This code path is not tenant-scoped — if intentional, "
                            + "annotate with @AllowCrossTenant; otherwise the filter is missing.");
        }
        return tid;
    }

    public static Optional<UUID> getOptional() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /** Snapshot the current value so it can be propagated to another thread. */
    public static UUID copy() {
        return CURRENT_TENANT.get();
    }

    /** Run a task with a specific tenant in context, restoring previous state after. */
    public static void runWith(UUID tenantId, Runnable task) {
        UUID previous = CURRENT_TENANT.get();
        try {
            if (tenantId != null) {
                CURRENT_TENANT.set(tenantId);
            } else {
                CURRENT_TENANT.remove();
            }
            task.run();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }
}
