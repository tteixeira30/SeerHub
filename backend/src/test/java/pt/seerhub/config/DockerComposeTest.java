package pt.seerhub.config;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import pt.seerhub.support.RepoRoot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R15.1a/b/c: o {@code docker-compose.yml} declara os três serviços da spec,
 * na ordem de arranque certa, com volumes persistentes, e sem segredos
 * literais (tudo por referência {@code ${VAR}}).
 *
 * <p>O arranque real de ponta a ponta não corre dentro desta suite (ver
 * plano de F00, secção 3); este teste faz o parse estrutural do ficheiro e
 * afirma tudo o que pode falhar por engano de configuração.
 */
@SuppressWarnings("unchecked")
class DockerComposeTest {

    private static Map<String, Object> compose;

    @BeforeAll
    static void carregarCompose() throws IOException {
        var caminho = RepoRoot.find().resolve("docker-compose.yml");
        try (var input = Files.newInputStream(caminho)) {
            compose = new Yaml().load(input);
        }
    }

    @Test
    void declaraOsTresServicosDaSpec() {
        Map<String, Object> servicos = (Map<String, Object>) compose.get("services");
        assertThat(servicos).containsKeys("db", "backend", "frontend");
    }

    @Test
    void backendEsperaPelaBaseDeDadosSaudavelEFrontendPeloBackend() {
        Map<String, Object> servicos = (Map<String, Object>) compose.get("services");

        Map<String, Object> backend = (Map<String, Object>) servicos.get("backend");
        Map<String, Object> dependenciasBackend = (Map<String, Object>) backend.get("depends_on");
        Map<String, Object> backendDependeDeDb = (Map<String, Object>) dependenciasBackend.get("db");
        assertThat(backendDependeDeDb.get("condition")).isEqualTo("service_healthy");

        Map<String, Object> frontend = (Map<String, Object>) servicos.get("frontend");
        Map<String, Object> dependenciasFrontend = (Map<String, Object>) frontend.get("depends_on");
        Map<String, Object> frontendDependeDeBackend = (Map<String, Object>) dependenciasFrontend.get("backend");
        assertThat(frontendDependeDeBackend.get("condition")).isEqualTo("service_healthy");

        Map<String, Object> db = (Map<String, Object>) servicos.get("db");
        assertThat(db).containsKey("healthcheck");
    }

    @Test
    void declaraVolumesPersistentesDeDadosEUploads() {
        Map<String, Object> volumes = (Map<String, Object>) compose.get("volumes");
        assertThat(volumes).containsKeys("pgdata", "uploads");
    }

    @Test
    void nenhumValorDeEnvironmentEUmLiteral() {
        Map<String, Object> servicos = (Map<String, Object>) compose.get("services");

        for (var entradaServico : servicos.entrySet()) {
            Map<String, Object> servico = (Map<String, Object>) entradaServico.getValue();
            Object environment = servico.get("environment");
            if (environment == null) {
                continue;
            }

            List<Map.Entry<String, Object>> entradas = extrairEntradasDeEnvironment(environment);
            for (var entrada : entradas) {
                Object valor = entrada.getValue();
                assertThat(valor)
                        .as("environment '%s' do serviço '%s' deve ser uma referência ${VAR}, não um literal",
                                entrada.getKey(), entradaServico.getKey())
                        .isInstanceOf(String.class);
                assertThat((String) valor)
                        .as("environment '%s' do serviço '%s' deve ser uma referência ${VAR}",
                                entrada.getKey(), entradaServico.getKey())
                        .matches("^\\$\\{[A-Z_][A-Z0-9_]*}$");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map.Entry<String, Object>> extrairEntradasDeEnvironment(Object environment) {
        if (environment instanceof Map) {
            return ((Map<String, Object>) environment).entrySet().stream().toList();
        }
        // Suporta também a forma de lista "CHAVE=${VALOR}", caso venha a ser usada.
        throw new IllegalStateException("Formato de 'environment' não suportado pelo teste: " + environment);
    }
}
