package com.jizhaoyu.chatbi.application.identity;

import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;

import java.util.Optional;

public interface IdentityPort {
    Optional<UserPrincipal> authenticate(String username, String password);
}
