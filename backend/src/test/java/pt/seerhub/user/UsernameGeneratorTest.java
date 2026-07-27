package pt.seerhub.user;

import java.util.Set;

import org.junit.jupiter.api.Test;

import pt.seerhub.user.service.UsernameGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1 — unitário puro, sem contexto Spring: a derivação do username a
 * partir do email é determinística, normaliza caracteres inválidos e
 * resolve colisões com sufixo numérico (D-6 do plano de F01).
 */
class UsernameGeneratorTest {

    @Test
    void derivaUsernameDoEmailENormalizaCaracteresInvalidos() {
        // Caso simples: local-part já válido.
        assertThat(UsernameGenerator.derivar("ana@exemplo.pt")).isEqualTo("ana");

        // Determinístico: o mesmo email produz sempre o mesmo resultado.
        assertThat(UsernameGenerator.derivar("ana@exemplo.pt"))
                .isEqualTo(UsernameGenerator.derivar("ana@exemplo.pt"));

        // Maiúsculas e caracteres inválidos são normalizados.
        assertThat(UsernameGenerator.derivar("Ana.Silva+Tips@Exemplo.PT")).isEqualTo("ana.silva-tips");

        // Repetições de separadores colapsam para um só.
        assertThat(UsernameGenerator.derivar("ana!!!silva@exemplo.pt")).isEqualTo("ana-silva");

        // Local-part demasiado curto (< 3) cai para o valor por omissão.
        assertThat(UsernameGenerator.derivar("ab@exemplo.pt")).isEqualTo("utilizador");

        // Cortado a 30 caracteres.
        String localLongo = "a".repeat(40) + "@exemplo.pt";
        assertThat(UsernameGenerator.derivar(localLongo)).hasSize(30);

        // Colisão: primeiro candidato existe, o segundo (com sufixo) não.
        String unico = UsernameGenerator.gerarUnico("ana@exemplo.pt", Set.of("ana", "ana-2")::contains);
        assertThat(unico).isEqualTo("ana-3");

        // Sem colisão nenhuma: devolve a base tal e qual.
        String semColisao = UsernameGenerator.gerarUnico("bruno@exemplo.pt", nome -> false);
        assertThat(semColisao).isEqualTo("bruno");
    }
}
