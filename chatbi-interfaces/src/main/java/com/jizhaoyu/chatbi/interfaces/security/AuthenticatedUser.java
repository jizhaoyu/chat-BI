package com.jizhaoyu.chatbi.interfaces.security;

import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.springframework.security.core.Authentication;

public final class AuthenticatedUser {
    private AuthenticatedUser() {
    }

    public static UserPrincipal require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new SecurityException("UNAUTHENTICATED");
        }
        return principal;
    }
}
