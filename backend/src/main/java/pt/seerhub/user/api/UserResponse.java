package pt.seerhub.user.api;

import java.time.Instant;

import pt.seerhub.user.domain.GlobalRole;
import pt.seerhub.user.domain.User;
import pt.seerhub.user.domain.UserStatus;

/** Representação pública de um utilizador, nunca inclui a password. */
public record UserResponse(
        Long id,
        String email,
        String username,
        String displayName,
        GlobalRole globalRole,
        UserStatus status,
        Instant createdAt) {

    public static UserResponse de(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getGlobalRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
