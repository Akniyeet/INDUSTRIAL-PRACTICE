package com.webizon.platform;

import com.webizon.auth.CurrentUser;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Spring bean used in {@code @PreAuthorize("@platformAdminGuard.check()")}
 * to gate access to the platform-admin API surface.
 *
 * <p>Looks up the caller's {@code is_platform_admin} flag from the local
 * {@code users} table. The {@code users} table is a global entity with no
 * RLS, so no tenant context is required; the {@link AllowCrossTenant}
 * annotation prevents the tenant listener from rejecting the call.
 */
@Component("platformAdminGuard")
@RequiredArgsConstructor
@AllowCrossTenant(reason = "users table is global; no tenant context required for platform admin check")
public class PlatformAdminGuard {

    private final UserRepository userRepository;

    /**
     * Returns {@code true} if the authenticated user has the
     * {@code is_platform_admin} flag set.
     *
     * <p>Used as {@code @PreAuthorize("@platformAdminGuard.check()")}.
     */
    public boolean check() {
        UUID keycloakId = CurrentUser.keycloakId();
        User user = userRepository.findByKeycloakId(keycloakId).orElse(null);
        return user != null && user.isPlatformAdmin();
    }

    /**
     * Same as {@link #check()} but throws {@code 403 Forbidden} instead of
     * returning false. Useful for imperative guards inside service methods.
     */
    public void require() {
        if (!check()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Platform admin access required");
        }
    }
}
