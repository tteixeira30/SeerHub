package pt.seerhub.tips;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.RepoRoot;
import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.domain.Market;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convenções mecânicas de F06a — verificáveis sem contexto Spring, mesmo padrão de {@code
 * FootballConventionsTest} (F05). Cobre 2i, X3–X6.
 */
class TipsConventionsTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void todaSelecaoProduzidaCabeNasColunasDoBaseline() throws IOException {
        Path v2 = RepoRoot.find().resolve("backend").resolve("src").resolve("main")
                .resolve("resources").resolve("db").resolve("migration").resolve("V2__baseline_schema.sql");
        String schema = Files.readString(v2);

        for (Market mercado : Market.values()) {
            assertThat(schema).contains("'" + mercado.name() + "'");
        }
        assertThat(Market.values()).hasSize(5);

        List<String> segmentosDeMercado = List.of(
                "1X2 1", "1X2 X", "1X2 2", "1X", "12", "X2",
                "O2.5", "U3.5", "BTTS S", "BTTS N", "GG", "NG",
                "H-1", "H+1.5", "H2+1.5");
        for (String segmento : segmentosDeMercado) {
            String linha = "28/07 Benfica - Porto | " + segmento + " | 1.85 | 2u";
            ParseResult resultado = parser.parse(linha, catalog);
            assertThat(resultado.temErros()).as("segmento '%s'", segmento).isFalse();
            String selecao = resultado.tips().get(0).selecoes().get(0).selecao();
            assertThat(selecao.length()).as("selecao '%s'", selecao).isLessThanOrEqualTo(20);
        }
    }

    @Test
    void aVersaoDoParserCabeNaColunaDoBaseline() {
        assertThat(TipFormat.VERSAO).isEqualTo("grammar-1");
        assertThat(TipFormat.VERSAO.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void oPacoteTipsNaoDependeDeBaseDeDadosNemDeRede() throws IOException {
        Path raizPrincipal = RepoRoot.find().resolve("backend").resolve("src").resolve("main")
                .resolve("java").resolve("pt").resolve("seerhub").resolve("tips");

        List<String> proibidos = List.of("jakarta.persistence", "springframework.data", "java.net.",
                "RestClient", "WebClient", "pt.seerhub.football.repo");

        try (Stream<Path> stream = Files.walk(raizPrincipal)) {
            List<Path> ficheiros = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path ficheiro : ficheiros) {
                String conteudo = Files.readString(ficheiro);
                for (String termo : proibidos) {
                    assertThat(conteudo).as("'%s' não deveria conter '%s'", ficheiro, termo).doesNotContain(termo);
                }
            }
        }
    }

    @Test
    void nenhumTesteDaGramaticaPrecisaDeContextoSpring() throws IOException {
        Path raizTestes = RepoRoot.find().resolve("backend").resolve("src").resolve("test")
                .resolve("java").resolve("pt").resolve("seerhub").resolve("tips");

        List<String> proibidos = List.of("@SpringBootTest", "AbstractIntegrationTest", "Testcontainers");

        try (Stream<Path> stream = Files.walk(raizTestes)) {
            List<Path> ficheiros = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    // Este próprio ficheiro tem de mencionar as strings proibidas para as poder procurar.
                    .filter(p -> !p.getFileName().toString().equals("TipsConventionsTest.java"))
                    .toList();
            assertThat(ficheiros).isNotEmpty();
            for (Path ficheiro : ficheiros) {
                String conteudo = Files.readString(ficheiro);
                for (String termo : proibidos) {
                    assertThat(conteudo).as("'%s' não deveria conter '%s'", ficheiro, termo).doesNotContain(termo);
                }
            }
        }
    }

    @Test
    void todosOsPadroesDoParserSaoConstantesCompiladasUmaSoVez() throws IOException {
        Path raizPrincipal = RepoRoot.find().resolve("backend").resolve("src").resolve("main")
                .resolve("java").resolve("pt").resolve("seerhub").resolve("tips");

        try (Stream<Path> stream = Files.walk(raizPrincipal)) {
            List<Path> ficheiros = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path ficheiro : ficheiros) {
                List<String> linhas = Files.readAllLines(ficheiro);
                for (String linha : linhas) {
                    if (linha.contains("Pattern.compile(")) {
                        assertThat(linha)
                                .as("'%s' devia declarar o Pattern como 'private static final' na mesma linha: %s",
                                        ficheiro, linha)
                                .contains("static final");
                    }
                }
            }
        }
    }
}
