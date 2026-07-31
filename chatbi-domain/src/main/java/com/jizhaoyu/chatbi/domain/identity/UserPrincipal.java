package com.jizhaoyu.chatbi.domain.identity;

import java.util.Set;
import java.util.UUID;

public record UserPrincipal(UUID userId, UUID tenantId, Set<Role> roles) {
    public UserPrincipal {
        roles = Set.copyOf(roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
