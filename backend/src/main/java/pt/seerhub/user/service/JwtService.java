package pt.seerhub.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.user.domain.GlobalRole;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Emite e valida o access token JWT (HS256, jjwt 0.12.6), D-4 do plano de
 * F01: transporta só identidade global ({@code sub}, {@code email},
 * {@code role}), nunca papéis por comunidade — esses são sempre lidos da
 * base de dados a cada pedido por F02+ (ver justificação ligada ao R3 no
 * plano).
 *
 * <p>Recusa construir-se com um segredo de menos de {@value #MINIMO_BYTES_SEGREDO}
 * bytes: HS256 com um segredo curto é criptograficamente fraco, e falhar no
 * arranque (em vez de na primeira autenticação) segue o mesmo espírito do
 * X2 de F00.
 */
public class JwtService {

    public static final String ISSUER = "seerhub";
    private static final int MINIMO_BYTES_SEGREDO = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(SeerHubProperties properties, Clock clock) {
        String segredo = properties.security().jwtSecret();
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMO_BYTES_SEGREDO) {
            throw new IllegalStateException(
                    "A propriedade 'seerhub.security.jwt-secret' tem de ter pelo menos "
                            + MINIMO_BYTES_SEGREDO + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = properties.security().accessTtl();
        this.clock = clock;
    }

    /** Emite um access token para o utilizador autenticado indicado. */
    public String gerarAccessToken(AuthenticatedUser autenticado) {
        Instant agora = Instant.now(clock);
        Instant expira = agora.plus(accessTtl);

        return Jwts.builder()
                .subject(String.valueOf(autenticado.id()))
                .claim(CLAIM_EMAIL, autenticado.email())
                .claim(CLAIM_ROLE, autenticado.role().name())
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expira))
                .signWith(key)
                .compact();
    }

    /**
     * Valida a assinatura e a expiração do token e reconstrói o principal.
     * Lança {@link io.jsonwebtoken.JwtException} (não verificada) em
     * qualquer situação inválida — token malformado, assinatura errada,
     * expirado — para o {@code JwtAuthenticationFilter} capturar de forma
     * uniforme e deixar o contexto de segurança vazio.
     */
    public AuthenticatedUser validarEDecodificar(String token) {
        Claims claims = Jwts.parser()
                .clock(() -> Date.from(Instant.now(clock)))
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long id = Long.valueOf(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        GlobalRole role = GlobalRole.valueOf(claims.get(CLAIM_ROLE, String.class));
        return new AuthenticatedUser(id, email, role);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }
}
