package pt.seerhub.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.RepoRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convenções de configuração transversais a todas as 16 features seguintes:
 * nenhum auto-DDL do Hibernate (R15.2b), segredos só por variável de
 * ambiente (R15.4a), nenhum segredo real commitado (R15.4b),
 * {@code .env} continua ignorado pelo git (R15.4c), e logs estruturados
 * por omissão (R15.6a).
 */
class ConfigurationConventionsTest {

    private static final Pattern PADRAO_HEX_LONGO = Pattern.compile("(?i)[0-9a-f]{32,}");
    private static final Pattern PADRAO_TOKEN_LONGO = Pattern.compile("[A-Za-z0-9_\\-]{40,}");
    private static final Pattern URL = Pattern.compile("https?://\\S+");

    @Test
    void nenhumPerfilUsaAutoDdlGeradorDeEsquema() throws IOException {
        Path recursos = RepoRoot.find().resolve("backend").resolve("src").resolve("main").resolve("resources");

        List<Path> ficheirosDeAplicacao;
        try (Stream<Path> stream = Files.list(recursos)) {
            ficheirosDeAplicacao = stream
                    .filter(p -> p.getFileName().toString().startsWith("application"))
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .toList();
        }
        Path perfilDeTeste = RepoRoot.find().resolve("backend").resolve("src").resolve("test")
                .resolve("resources").resolve("application-test.yml");

        List<Path> todos = new ArrayList<>(ficheirosDeAplicacao);
        if (Files.exists(perfilDeTeste)) {
            todos.add(perfilDeTeste);
        }

        assertThat(todos).isNotEmpty();

        Pattern ddlAuto = Pattern.compile("ddl-auto:\\s*(\\S+)");
        for (Path ficheiro : todos) {
            String conteudo = Files.readString(ficheiro);
            Matcher matcher = ddlAuto.matcher(conteudo);
            while (matcher.find()) {
                String valor = matcher.group(1);
                assertThat(valor)
                        .as("ddl-auto em '%s' nunca pode gerar esquema", ficheiro)
                        .isEqualTo("validate");
            }
        }
    }

    @Test
    void segredosSaoSempreReferenciasAVariaveisDeAmbiente() throws IOException {
        Path applicationYml = RepoRoot.find().resolve("backend").resolve("src").resolve("main")
                .resolve("resources").resolve("application.yml");
        String conteudo = Files.readString(applicationYml);

        assertThat(conteudo).contains("jwt-secret: ${JWT_SECRET}");
        assertThat(conteudo).containsPattern(Pattern.compile("api-key:\\s*\\$\\{API_FOOTBALL_KEY[:}]"));
    }

    @Test
    void nenhumFicheiroVersionadoContemSegredoComAparenciaReal() throws IOException {
        Path raiz = RepoRoot.find();
        List<Path> aVerificar = new ArrayList<>();
        aVerificar.add(raiz.resolve(".env.example"));
        aVerificar.add(raiz.resolve("docker-compose.yml"));

        Path recursos = raiz.resolve("backend").resolve("src").resolve("main").resolve("resources");
        try (Stream<Path> stream = Files.walk(recursos)) {
            stream.filter(Files::isRegularFile).forEach(aVerificar::add);
        }

        for (Path ficheiro : aVerificar) {
            if (!Files.exists(ficheiro)) {
                continue;
            }
            for (String linha : Files.readAllLines(ficheiro)) {
                String semEspacos = linha.strip();
                boolean linhaDeComentario = semEspacos.startsWith("#") || semEspacos.startsWith("--")
                        || semEspacos.startsWith("//");
                if (linhaDeComentario || semEspacos.isEmpty()) {
                    continue;
                }

                String semUrls = URL.matcher(linha).replaceAll("");

                assertThat(PADRAO_HEX_LONGO.matcher(semUrls).find())
                        .as("possível segredo real (hex longo) em '%s': %s", ficheiro, linha)
                        .isFalse();
                assertThat(PADRAO_TOKEN_LONGO.matcher(semUrls).find())
                        .as("possível segredo real (token longo) em '%s': %s", ficheiro, linha)
                        .isFalse();
            }
        }
    }

    @Test
    void gitignoreIgnoraOEnvEPermiteOEnvExample() throws IOException {
        Path gitignore = RepoRoot.find().resolve(".gitignore");
        List<String> linhas = Files.readAllLines(gitignore).stream().map(String::strip).toList();

        assertThat(linhas).contains(".env");
        assertThat(linhas).contains("!.env.example");
    }

    @Test
    void perfilPorOmissaoEmiteLogsEstruturados() throws IOException {
        Path applicationYml = RepoRoot.find().resolve("backend").resolve("src").resolve("main")
                .resolve("resources").resolve("application.yml");
        String conteudo = Files.readString(applicationYml);

        assertThat(conteudo).containsPattern(Pattern.compile("structured:\\s*\\n\\s*format:\\s*\\n\\s*console:\\s*ecs"));
    }
}
