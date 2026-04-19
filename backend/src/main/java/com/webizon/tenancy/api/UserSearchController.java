package com.webizon.tenancy.api;

import com.webizon.auth.CurrentUser;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User search for moderator assignment in the event wizard.
 *
 * <p>Returns users within the same tenant matching a name/email query.
 * Capped at 10 results to prevent full-table scans.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserRepository userRepository;

    public record UserSearchResult(
            UUID id,
            String fullName,
            String email
    ) {}

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    public List<UserSearchResult> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        int capped = Math.min(Math.max(limit, 1), 20);
        String pattern = "%" + query.trim().toLowerCase() + "%";

        return userRepository.searchByNameOrEmail(pattern, capped)
                .stream()
                .map(u -> new UserSearchResult(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
    }
}
