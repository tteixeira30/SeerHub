package pt.seerhub.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Localiza a raiz do repositório a partir do diretório de trabalho do
 * processo de testes, subindo diretórios até encontrar um que contenha
 * {@code docker-compose.yml} e {@code .env.example}.
 *
 * <p>Usado pelos testes de configuração (que leem ficheiros na raiz do
 * monorepo, fora do classpath do módulo backend).
 */
public final class RepoRoot {

    private RepoRoot() {
    }

    public static Path find() {
        Path atual = Path.of("").toAbsolutePath();
        while (atual != null) {
            boolean temCompose = Files.exists(atual.resolve("docker-compose.yml"));
            boolean temEnvExample = Files.exists(atual.resolve(".env.example"));
            if (temCompose && temEnvExample) {
                return atual;
            }
            atual = atual.getParent();
        }
        throw new UncheckedIOException(new IOException(
                "Não foi possível localizar a raiz do repositório "
                        + "(esperava docker-compose.yml e .env.example algures acima de "
                        + Path.of("").toAbsolutePath() + ")"));
    }
}
