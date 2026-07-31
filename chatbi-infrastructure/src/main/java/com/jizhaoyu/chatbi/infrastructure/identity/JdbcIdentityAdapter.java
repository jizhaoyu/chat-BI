package com.jizhaoyu.chatbi.infrastructure.identity;

import com.jizhaoyu.chatbi.application.identity.IdentityPort;
import com.jizhaoyu.chatbi.domain.identity.Role;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcIdentityAdapter implements IdentityPort {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public JdbcIdentityAdapter(@Qualifier("platformJdbcTemplate") JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public Optional<UserPrincipal> authenticate(String username, String password) {
        return jdbc.query("SELECT id, tenant_id, password_hash FROM app_user WHERE username = ? AND enabled = TRUE",
                (rs, row) -> new UserRecord(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("tenant_id")), rs.getString("password_hash")), username)
                .stream().filter(user -> encoder.matches(password, user.passwordHash())).findFirst().map(user -> {
                    Set<Role> roles = jdbc.queryForList("SELECT role_name FROM app_user_role WHERE user_id = ?", String.class, user.id().toString())
                            .stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
                    return new UserPrincipal(user.id(), user.tenantId(), roles);
                });
    }

    private record UserRecord(UUID id, UUID tenantId, String passwordHash) {}
}
