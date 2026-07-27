package pt.seerhub.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import io.jsonwebtoken.ExpiredJwtException;

import org.junit.jupiter.api.Test;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.user.domain.GlobalRole;
import pt.seerhub.user.security.AuthenticatedUser;
import pt.seerhub.user.service.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 2b, E2, E3 — unitário puro, sem contexto Spring: {@link JwtService}
 * usa {@link Clock#fixed} para que a expiração seja testável sem esperar
 * tempo real (regra dura do CLAUDE.md: nenhum teste dorme para provar TTL).
 */
class JwtServiceTest {

    private static final String SEGREDO_VALIDO = "segredo-de-teste-com-trinta-e-dois-bytes!!";

    @Test
    void accessTokenExpiraExatamenteApósOAccessTtlConfigurado() {
        Instant t0 = Instant.parse("2026-07-27T10:00:00Z");
        Duration ttl = Duration.ofMinutes(15);
        SeerHubProperties properties = propriedades(SEGREDO_VALIDO, ttl);

        Clock relogioNaEmissao = Clock.fixed(t0, ZoneOffset.UTC);
        JwtService servicoNaEmissao = new JwtService(properties, relogioNaEmissao);
        String token = servicoNaEmissao.gerarAccessToken(
                new AuthenticatedUser(1L, "ana@exemplo.pt", GlobalRole.USER));

        // Um segundo antes de expirar: ainda válido.
        Clock relogioAntesDeExpirar = Clock.fixed(t0.plus(ttl).minusSeconds(1), ZoneOffset.UTC);
        JwtService servicoAntesDeExpirar = new JwtService(properties, relogioAntesDeExpirar);
        assertThat(servicoAntesDeExpirar.validarEDecodificar(token).id()).isEqualTo(1L);

        // Um segundo depois de expirar: já não é válido.
        Clock relogioDepoisDeExpirar = Clock.fixed(t0.plus(ttl).plusSeconds(1), ZoneOffset.UTC);
        JwtService servicoDepoisDeExpirar = new JwtService(properties, relogioDepoisDeExpirar);
        assertThatThrownBy(() -> servicoDepoisDeExpirar.validarEDecodificar(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void segredoComMenosDeTrintaEDoisBytesFalhaComMensagemQueNomeiaAPropriedade() {
        SeerHubProperties properties = propriedades("segredo-com-menos-de-32-bytes", Duration.ofMinutes(15));
        // Confirma que o segredo escolhido para o teste é mesmo curto (< 32 bytes UTF-8).
        assertThat("segredo-com-menos-de-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThan(32);

        assertThatThrownBy(() -> new JwtService(properties, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seerhub.security.jwt-secret");
    }

    @Test
    void tokenTransportaIdEmailEPapelEEDecodificadoDeVolta() {
        SeerHubProperties properties = propriedades(SEGREDO_VALIDO, Duration.ofMinutes(15));
        JwtService servico = new JwtService(properties, Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC));

        AuthenticatedUser original = new AuthenticatedUser(42L, "admin@exemplo.pt", GlobalRole.ADMIN);
        String token = servico.gerarAccessToken(original);

        AuthenticatedUser decodificado = servico.validarEDecodificar(token);

        assertThat(decodificado.id()).isEqualTo(original.id());
        assertThat(decodificado.email()).isEqualTo(original.email());
        assertThat(decodificado.role()).isEqualTo(original.role());
    }

    private static SeerHubProperties propriedades(String segredo, Duration accessTtl) {
        return new SeerHubProperties(
                new SeerHubProperties.Security(segredo, accessTtl, Duration.ofDays(30)),
                new SeerHubProperties.Football(null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                new SeerHubProperties.Uploads(null));
    }
}
