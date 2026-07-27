package pt.seerhub.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2: convenção de nomenclatura das migrações, verificável mecanicamente
 * pelas 16 features seguintes sem teste de integração — cada migração é
 * {@code V<n>__<snake_case>.sql}, com número de versão único.
 */
class MigrationNamingTest {

    private static final Pattern PADRAO = Pattern.compile("^V(\\d+)__[a-z0-9_]+\\.sql$");

    @Test
    void todosOsFicheirosDeMigracaoSeguemAConvencaoEVersaoUnica() throws IOException {
        Path diretorioMigracoes = Path.of("src", "main", "resources", "db", "migration");
        assertThat(Files.isDirectory(diretorioMigracoes))
                .as("diretório de migrações deve existir: %s", diretorioMigracoes.toAbsolutePath())
                .isTrue();

        List<Path> ficheiros;
        try (Stream<Path> stream = Files.list(diretorioMigracoes)) {
            ficheiros = stream.sorted().toList();
        }

        assertThat(ficheiros).isNotEmpty();

        Set<String> versoesVistas = new HashSet<>();
        for (Path ficheiro : ficheiros) {
            String nome = ficheiro.getFileName().toString();
            Matcher matcher = PADRAO.matcher(nome);
            assertThat(matcher.matches())
                    .as("ficheiro '%s' deve seguir o padrão V<n>__<snake_case>.sql", nome)
                    .isTrue();

            String versao = matcher.group(1);
            assertThat(versoesVistas.add(versao))
                    .as("versão '%s' repetida entre as migrações", versao)
                    .isTrue();
        }
    }
}
