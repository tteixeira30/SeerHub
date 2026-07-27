package pt.seerhub.user.api;

/** Resposta comum a registo, login e refresh (D-12 do plano de F01). */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
