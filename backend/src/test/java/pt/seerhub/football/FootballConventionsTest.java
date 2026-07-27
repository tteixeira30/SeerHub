package pt.seerhub.football;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.RepoRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convenções mecânicas de F05 — verificáveis sem contexto Spring, lendo
 * ficheiros via {@link RepoRoot}, mesmo padrão de
 * {@code ConfigurationConventionsTest}/{@code EnvExampleTest} (F00).
 */
class FootballConventionsTest {

    @Test
    void asVariaveisNovasEstaoNoEnvExampleENoDockerCompose() throws IOException {
        Path raiz = RepoRoot.find();
        String envExample = Files.readString(raiz.resolve(".env.example"));
        String compose = Files.readString(raiz.resolve("docker-compose.yml"));

        assertThat(envExample).contains("API_FOOTBALL_DAILY_BUDGET");
        assertThat(envExample).contains("API_FOOTBALL_SEASON");
        assertThat(compose).contains("API_FOOTBALL_DAILY_BUDGET");
        assertThat(compose).contains("API_FOOTBALL_SEASON");
    }

    @Test
    void aVariavelDaEpocaEstaNoEnvExample() throws IOException {
        Path raiz = RepoRoot.find();
        List<String> linhas = Files.readAllLines(raiz.resolve(".env.example"));
        boolean existe = linhas.stream().anyMatch(l -> l.strip().startsWith("API_FOOTBALL_SEASON="));
        assertThat(existe).isTrue();
    }

    @Test
    void soOServicoDeSincronizacaoDependeDoFornecedor() throws IOException {
        Path raizPrincipal = RepoRoot.find().resolve("backend").resolve("src").resolve("main").resolve("java");

        // \b evita falsos positivos em "ApiFootballDataProvider"/"FixedFootballDataProvider"
        // (que legitimamente implementam a interface, e já estão na lista de permitidos).
        Pattern referenciaAoTipo = Pattern.compile("\\bFootballDataProvider\\b");

        List<Path> ficheirosComReferencia;
        try (Stream<Path> stream = Files.walk(raizPrincipal)) {
            ficheirosComReferencia = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> contem(p, referenciaAoTipo))
                    .toList();
        }

        List<String> permitidos = List.of(
                Path.of("football", "provider", "FootballDataProvider.java").toString(),
                Path.of("football", "provider", "ApiFootballDataProvider.java").toString(),
                Path.of("football", "provider", "FixedFootballDataProvider.java").toString(),
                Path.of("football", "service", "FootballSyncService.java").toString(),
                Path.of("football", "config", "FootballProviderConfig.java").toString());

        for (Path ficheiro : ficheirosComReferencia) {
            String caminho = ficheiro.toString();
            boolean ehPermitido = permitidos.stream().anyMatch(caminho::endsWith);
            assertThat(ehPermitido)
                    .as("'%s' não deveria depender de FootballDataProvider", ficheiro)
                    .isTrue();
        }
    }

    @Test
    void oPerfilDeTesteUsaDadosFixosEOPerfilPorOmissaoOFornecedorReal() throws IOException {
        Path raiz = RepoRoot.find().resolve("backend");
        String applicationYml = Files.readString(raiz.resolve("src").resolve("main")
                .resolve("resources").resolve("application.yml"));
        String applicationTestYml = Files.readString(raiz.resolve("src").resolve("test")
                .resolve("resources").resolve("application-test.yml"));

        assertThat(applicationYml).containsPattern(Pattern.compile("provider:\\s*api-football"));
        assertThat(applicationTestYml).containsPattern(Pattern.compile("provider:\\s*fake"));
    }

    @Test
    void nenhumRecursoDeTesteDeclaraChaveOuUrlDoFornecedorReal() throws IOException {
        Path raizTeste = RepoRoot.find().resolve("backend").resolve("src").resolve("test");

        List<Path> ficheiros;
        try (Stream<Path> stream = Files.walk(raizTeste)) {
            ficheiros = stream.filter(Files::isRegularFile).toList();
        }

        // Domínio real do fornecedor, montado por concatenação de propósito — para este
        // próprio ficheiro de teste (que tem de mencionar a string para a poder procurar)
        // não se autoacusar na varredura.
        String dominioReal = "api-sports" + ".io";

        for (Path ficheiro : ficheiros) {
            if (ficheiro.getFileName().toString().equals("FootballConventionsTest.java")) {
                continue;
            }
            String conteudo = Files.readString(ficheiro);
            assertThat(conteudo)
                    .as("'%s' não deve referir o domínio real do fornecedor", ficheiro)
                    .doesNotContain(dominioReal);
        }
    }

    private static boolean contem(Path ficheiro, Pattern padrao) {
        try {
            return padrao.matcher(Files.readString(ficheiro)).find();
        } catch (IOException e) {
            return false;
        }
    }
}
