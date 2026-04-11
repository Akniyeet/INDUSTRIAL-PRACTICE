package com.webizon.tenancy.api;

import com.webizon.tenancy.api.dto.MembershipResponse;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.service.MembershipService;
import com.webizon.tenancy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/v1/memberships} — list the caller's memberships across all
 * tenants. Used by the workspace switcher in the frontend header.
 */
@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final UserService userService;
    private final MembershipService membershipService;

    @GetMapping("/me")
    public List<MembershipResponse> myMemberships() {
        User me = userService.bootstrapFromJwt();
        return membershipService.listMembershipsForUser(me.getId()).stream()
                .map(MembershipResponse::from)
                .toList();
    }
}
