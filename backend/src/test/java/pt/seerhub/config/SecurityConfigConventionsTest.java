package pt.seerhub.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.RepoRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X4 do plano de F04: guarda mecânica, sem contexto Spring, contra
 * reintroduzir um caminho interno público em {@code SecurityConfig} — a
 * dívida herdada {@code requestMatchers("/__test__/**").permitAll()} não
 * pode voltar a aparecer.
 */
class SecurityConfigConventionsTest {

    @Test
    void aCadeiaDeSegurancaNaoLibertaNenhumCaminhoInterno() throws IOException {
        Path securityConfig = RepoRoot.find().resolve("backend").resolve("src").resolve("main")
                .resolve("java").resolve("pt").resolve("seerhub").resolve("config").resolve("SecurityConfig.java");

        String conteudo = Files.readString(securityConfig);

        assertThat(conteudo).doesNotContain("__test__");
    }
}
