package pt.seerhub.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.user.api.AuthResponse;
import pt.seerhub.user.api.LoginRequest;
import pt.seerhub.user.api.LogoutRequest;
import pt.seerhub.user.api.RefreshRequest;
import pt.seerhub.user.api.RegisterRequest;
import pt.seerhub.user.api.UserResponse;
import pt.seerhub.user.domain.RefreshToken;
import pt.seerhub.user.domain.User;
import pt.seerhub.user.domain.UserStatus;
import pt.seerhub.user.repo.RefreshTokenRepository;
import pt.seerhub.user.repo.UserRepository;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Regras de negócio de registo, login, refresh e logout (R1).
 *
 * <p>As mensagens de erro são constantes públicas porque {@code RegistrationIT}
 * e {@code LoginIT} as assertam literalmente — ver critério 3 do plano de
 * F01: nenhuma delas pode revelar a existência de uma conta.
 */
@Service
public class AuthService {

    public static final String MENSAGEM_REGISTO_RECUSADO =
            "Não foi possível concluir o registo com os dados indicados.";
    public static final String MENSAGEM_LOGIN_INVALIDO = "Email ou password incorretos.";
    public static final String MENSAGEM_REFRESH_INVALIDO = "Sessão inválida ou expirada.";

    private static final int BYTES_TOKEN_REFRESH = 32;
    private static final Base64.Encoder BASE64_URL_SEM_PADDING = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SeerHubProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SeerHubProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizarEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_REGISTO_RECUSADO);
        }

        String username = UsernameGenerator.gerarUnico(email, userRepository::existsByUsername);
        String displayName = temTexto(request.displayName()) ? request.displayName() : username;
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(email, passwordHash, username, displayName, clock);

        try {
            userRepository.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Corrida entre dois pedidos concorrentes com o mesmo email:
            // a restrição UNIQUE da base de dados apanha o que a verificação
            // acima não apanhou. Mesma mensagem genérica, mesmo critério 3.
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_REGISTO_RECUSADO);
        }

        return emitirTokens(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizarEmail(request.email());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getStatus() == UserStatus.SUSPENDED
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Email desconhecido, password errada e conta suspensa devolvem
            // exatamente a mesma exceção: nenhum dos três contextos pode
            // servir de oráculo sobre a existência ou o estado da conta
            // (critério 3b e X5 do plano de F01).
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_LOGIN_INVALIDO);
        }

        return emitirTokens(user, UUID.randomUUID());
    }

    // noRollbackFor é essencial aqui: quando se deteta reutilização, a
    // revogação de toda a família (revogarFamilia) tem de sobreviver mesmo
    // que o método a seguir lance ApiException — sem isto, o comportamento
    // por omissão do Spring desfaz o UPDATE em bloco no rollback e a
    // revogação em cascata do D-3 não teria efeito nenhum (X1).
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256Hex(request.refreshToken());
        RefreshToken tokenAtual = refreshTokenRepository.findByTokenHash(hash).orElse(null);

        if (tokenAtual == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_REFRESH_INVALIDO);
        }

        Instant agora = Instant.now(clock);

        if (tokenAtual.isRevoked()) {
            // Reutilização de um token já rodado: sinal de roubo ou de
            // pedido duplicado (D-3). Revoga-se toda a família por defesa
            // em profundidade, não só o token recebido.
            refreshTokenRepository.revogarFamilia(tokenAtual.getFamilyId(), agora);
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_REFRESH_INVALIDO);
        }

        if (tokenAtual.isExpired(agora)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_REFRESH_INVALIDO);
        }

        EmissaoTokens emissao = emitirTokensComEntidade(tokenAtual.getUser(), tokenAtual.getFamilyId());

        // Marca o token usado como revogado e liga-o ao sucessor só depois
        // de o novo já estar gravado, para o par "revogado + substituto"
        // ser sempre consistente.
        tokenAtual.setRevokedAt(agora);
        tokenAtual.setReplacedById(emissao.refreshToken().getId());
        refreshTokenRepository.save(tokenAtual);

        return emissao.resposta();
    }

    @Transactional
    public void logout(LogoutRequest request, AuthenticatedUser autenticado) {
        String hash = sha256Hex(request.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);

        // Idempotente e silencioso (D-12): revogar um token desconhecido ou
        // de outro utilizador não revoga nada e não é distinguível de um
        // logout bem-sucedido, para o endpoint não se tornar um oráculo.
        if (token != null && token.getUser().getId().equals(autenticado.id()) && !token.isRevoked()) {
            token.setRevokedAt(Instant.now(clock));
            refreshTokenRepository.save(token);
        }
    }

    private AuthResponse emitirTokens(User user, UUID familyId) {
        return emitirTokensComEntidade(user, familyId).resposta();
    }

    private EmissaoTokens emitirTokensComEntidade(User user, UUID familyId) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getGlobalRole());
        String accessToken = jwtService.gerarAccessToken(principal);

        String refreshTokenBruto = gerarRefreshTokenBruto();
        Instant agora = Instant.now(clock);
        Instant expiraEm = agora.plus(properties.security().refreshTtl());

        RefreshToken refreshToken = new RefreshToken(user, sha256Hex(refreshTokenBruto), familyId, agora, expiraEm);
        refreshTokenRepository.save(refreshToken);

        AuthResponse resposta = new AuthResponse(
                accessToken,
                refreshTokenBruto,
                "Bearer",
                properties.security().accessTtl().toSeconds(),
                UserResponse.de(user));

        return new EmissaoTokens(resposta, refreshToken);
    }

    private record EmissaoTokens(AuthResponse resposta, RefreshToken refreshToken) {
    }

    private String gerarRefreshTokenBruto() {
        byte[] bytes = new byte[BYTES_TOKEN_REFRESH];
        secureRandom.nextBytes(bytes);
        return BASE64_URL_SEM_PADDING.encodeToString(bytes);
    }

    private static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 tem de estar disponível na JVM.", ex);
        }
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
