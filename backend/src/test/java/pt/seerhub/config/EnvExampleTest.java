package pt.seerhub.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.RepoRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R15.3a/b/c: {@code .env.example} tem exatamente as variáveis usadas em
 * configuração/compose — nem a menos (R15.3a), nem a mais (R15.3b) — e
 * nenhum valor com aparência de segredo real (R15.3c).
 */
class EnvExampleTest {

    // Aceita ${VAR}, ${VAR:default} (estilo Spring) e ${VAR:-default} (estilo shell/compose).
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(:-?[^}]*)?}");

    private static final List<Path> FICHEIROS_DE_CONFIGURACAO = List.of(
            Path.of("src", "main", "resources", "application.yml"),
            Path.of("src", "main", "resources", "application-local.yml")
    );

    @Test
    void todaVariavelUsadaEmConfiguracaoOuComposeEstaNoEnvExample() throws IOException {
        Set<String> usadas = variaveisUsadasEmConfiguracaoEDockerCompose();
        Set<String> doEnvExample = chavesDoEnvExample();

        Set<String> emFalta = new LinkedHashSet<>(usadas);
        emFalta.removeAll(doEnvExample);

        assertThat(emFalta)
                .as(".env.example deve conter todas as variáveis usadas em application*.yml e docker-compose.yml")
                .isEmpty();
    }

    @Test
    void todaVariavelDoEnvExampleEUsadaEmConfiguracaoOuCompose() throws IOException {
        Set<String> usadas = variaveisUsadasEmConfiguracaoEDockerCompose();
        Set<String> doEnvExample = chavesDoEnvExample();

        Set<String> aMais = new LinkedHashSet<>(doEnvExample);
        aMais.removeAll(usadas);

        assertThat(aMais)
                .as(".env.example não deve ter variáveis por usar (lixo copiado)")
                .isEmpty();
    }

    @Test
    void variaveisDeSegredoTemApenasPlaceholders() throws IOException {
        Path envExample = RepoRoot.find().resolve(".env.example");
        List<String> linhas = Files.readAllLines(envExample);

        for (String linha : linhas) {
            String semComentario = linha.strip();
            if (semComentario.isEmpty() || semComentario.startsWith("#") || !semComentario.contains("=")) {
                continue;
            }
            String chave = semComentario.substring(0, semComentario.indexOf('=')).strip();
            String valor = semComentario.substring(semComentario.indexOf('=') + 1).strip();

            if (chave.equals("JWT_SECRET") || chave.equals("POSTGRES_PASSWORD")) {
                assertThat(valor)
                        .as("'%s' no .env.example deve ser um placeholder óbvio, não um segredo real", chave)
                        .containsIgnoringCase("change-me");
            }
        }
    }

    private static Set<String> variaveisUsadasEmConfiguracaoEDockerCompose() throws IOException {
        Path raiz = RepoRoot.find();
        Set<String> variaveis = new LinkedHashSet<>();

        for (Path relativo : FICHEIROS_DE_CONFIGURACAO) {
            Path caminho = raiz.resolve("backend").resolve(relativo);
            variaveis.addAll(extrairPlaceholders(caminho));
        }
        variaveis.addAll(extrairPlaceholders(raiz.resolve("docker-compose.yml")));

        return variaveis;
    }

    private static Set<String> extrairPlaceholders(Path ficheiro) throws IOException {
        if (!Files.exists(ficheiro)) {
            return Set.of();
        }
        String conteudo = Files.readString(ficheiro);
        Set<String> encontradas = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(conteudo);
        while (matcher.find()) {
            encontradas.add(matcher.group(1));
        }
        return encontradas;
    }

    private static Set<String> chavesDoEnvExample() throws IOException {
        Path envExample = RepoRoot.find().resolve(".env.example");
        Set<String> chaves = new LinkedHashSet<>();
        for (String linha : Files.readAllLines(envExample)) {
            String semComentario = linha.strip();
            if (semComentario.isEmpty() || semComentario.startsWith("#") || !semComentario.contains("=")) {
                continue;
            }
            chaves.add(semComentario.substring(0, semComentario.indexOf('=')).strip());
        }
        return chaves;
    }
}
