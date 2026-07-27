package pt.seerhub.football.provider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.FixtureStatus;

/**
 * Implementação real de {@link FootballDataProvider}, atrás da API-Football
 * (R5, critério 7). Escrita deliberadamente tarde na ordem de implementação
 * (passo 10 do plano de F05): nada na suite depende dela, e isso garante que
 * o motor de sincronização não foi moldado à forma deste fornecedor em
 * particular.
 *
 * <p><b>GOLOS GRAVADOS (aviso repetido do Javadoc de
 * {@link ApiFootballStatusMapper}, para não voltar a ser questionado por
 * F08): usa-se sempre {@code score.fulltime}, nunca {@code goals}
 * diretamente — {@code goals} inclui o prolongamento.</b>
 *
 * <p>Sem chave configurada, lança {@link FootballProviderException#semChave()}
 * na primeira chamada (nunca no arranque — {@code seerhub.football.api-key}
 * não tem {@code @NotBlank} de propósito, ver {@code SeerHubProperties}).
 * Parsing feito com {@link JsonNode} (não com DTOs tipados), resiliente ao
 * campo {@code errors} que a API ora devolve como objeto, ora como lista.
 */
public class ApiFootballDataProvider implements FootballDataProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballDataProvider.class);

    private static final String CABECALHO_CHAVE = "x-apisports-key";
    private static final String CABECALHO_RESTANTES = "x-ratelimit-requests-remaining";

    private final RestClient restClient;
    private final SeerHubProperties properties;

    public ApiFootballDataProvider(RestClient restClient, SeerHubProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<ProviderFixture> jogosDaLigaEntre(long ligaProviderId, int epoca, LocalDate de, LocalDate ate) {
        JsonNode resposta = pedirFixtures(ligaProviderId, epoca, Map.of(
                "from", de.toString(), "to", ate.toString(), "timezone", "UTC"));
        return interpretarFixtures(resposta);
    }

    @Override
    public List<ProviderFixture> jogosDaLigaNoDia(long ligaProviderId, int epoca, LocalDate dia) {
        JsonNode resposta = pedirFixtures(ligaProviderId, epoca, Map.of(
                "date", dia.toString(), "timezone", "UTC"));
        return interpretarFixtures(resposta);
    }

    @Override
    public List<ProviderTeam> equipasDaLiga(long ligaProviderId, int epoca) {
        verificarChave();
        JsonNode resposta = pedir("/teams", Map.of(
                "league", String.valueOf(ligaProviderId), "season", String.valueOf(epoca)));
        List<ProviderTeam> equipas = new ArrayList<>();
        for (JsonNode entrada : arrayDeResposta(resposta)) {
            JsonNode team = entrada.path("team");
            equipas.add(new ProviderTeam(
                    team.path("id").asLong(),
                    team.path("name").asText(null),
                    team.path("code").asText(null),
                    team.path("country").asText(null),
                    team.path("logo").asText(null)));
        }
        return equipas;
    }

    @Override
    public Optional<byte[]> emblema(String url) {
        // Não é chamada de dados — nunca consome orçamento (§2.4-C do plano).
        try {
            byte[] bytes = restClient.get().uri(url).retrieve().body(byte[].class);
            return Optional.ofNullable(bytes);
        } catch (RestClientException ex) {
            log.debug("Falha a descarregar emblema de '{}': {}", url, ex.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode pedirFixtures(long ligaProviderId, int epoca, Map<String, String> extra) {
        verificarChave();
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("league", String.valueOf(ligaProviderId));
        parametros.put("season", String.valueOf(epoca));
        parametros.putAll(extra);
        return pedir("/fixtures", parametros);
    }

    private void verificarChave() {
        String chave = properties.football() != null ? properties.football().apiKey() : null;
        if (chave == null || chave.isBlank()) {
            throw FootballProviderException.semChave();
        }
    }

    private JsonNode pedir(String caminho, Map<String, String> parametros) {
        org.springframework.util.MultiValueMap<String, String> queryParams = new org.springframework.util.LinkedMultiValueMap<>();
        parametros.forEach(queryParams::add);
        String uri = UriComponentsBuilder.fromPath(caminho)
                .queryParams(queryParams)
                .build().toUriString();

        String chave = properties.football().apiKey();
        try {
            return restClient.get()
                    .uri(uri)
                    .header(CABECALHO_CHAVE, chave)
                    .exchange((request, response) -> {
                        String restantes = response.getHeaders().getFirst(CABECALHO_RESTANTES);
                        if ("0".equals(restantes)) {
                            throw FootballProviderException.quotaEsgotada();
                        }
                        if (response.getStatusCode().value() == 429) {
                            throw FootballProviderException.quotaEsgotada();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw FootballProviderException.respostaInvalida(
                                    "estado HTTP " + response.getStatusCode().value());
                        }
                        return lerCorpo(response);
                    });
        } catch (FootballProviderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw FootballProviderException.falhaDeRede(ex);
        }
    }

    private JsonNode lerCorpo(org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode raiz = objectMapper.readTree(response.getBody());
            verificarErros(raiz);
            return raiz;
        } catch (java.io.IOException ex) {
            throw FootballProviderException.respostaInvalida("corpo ilegível: " + ex.getMessage());
        }
    }

    /** O campo "errors" da API-Football ora vem como objeto {@code {}}, ora como lista {@code []}. */
    private void verificarErros(JsonNode raiz) {
        JsonNode erros = raiz.path("errors");
        if (erros.isMissingNode() || erros.isNull()) {
            return;
        }
        boolean temErros = (erros.isObject() && erros.size() > 0) || (erros.isArray() && erros.size() > 0);
        if (temErros) {
            throw FootballProviderException.respostaInvalida(erros.toString());
        }
    }

    private List<JsonNode> arrayDeResposta(JsonNode raiz) {
        List<JsonNode> lista = new ArrayList<>();
        JsonNode resposta = raiz.path("response");
        if (resposta.isArray()) {
            resposta.forEach(lista::add);
        }
        return lista;
    }

    private List<ProviderFixture> interpretarFixtures(JsonNode raiz) {
        List<ProviderFixture> resultado = new ArrayList<>();
        for (JsonNode entrada : arrayDeResposta(raiz)) {
            resultado.add(interpretarFixture(entrada));
        }
        return resultado;
    }

    private ProviderFixture interpretarFixture(JsonNode entrada) {
        JsonNode fixtureNode = entrada.path("fixture");
        JsonNode leagueNode = entrada.path("league");
        JsonNode teamsNode = entrada.path("teams");
        JsonNode homeNode = teamsNode.path("home");
        JsonNode awayNode = teamsNode.path("away");

        ProviderLeague liga = new ProviderLeague(
                leagueNode.path("id").asLong(),
                leagueNode.path("name").asText(null),
                leagueNode.path("country").asText(null),
                leagueNode.path("logo").asText(null),
                leagueNode.path("season").asInt());

        ProviderTeam casa = new ProviderTeam(homeNode.path("id").asLong(), homeNode.path("name").asText(null),
                null, null, homeNode.path("logo").asText(null));
        ProviderTeam fora = new ProviderTeam(awayNode.path("id").asLong(), awayNode.path("name").asText(null),
                null, null, awayNode.path("logo").asText(null));

        String codigoEstado = fixtureNode.path("status").path("short").asText(null);
        FixtureStatus status = ApiFootballStatusMapper.mapear(codigoEstado).orElse(null);

        Integer[] golos = ApiFootballStatusMapper.golosRegulamentares(entrada);

        Instant kickoffAt = interpretarInstante(fixtureNode.path("date").asText(null));

        return new ProviderFixture(fixtureNode.path("id").asLong(), liga, casa, fora, kickoffAt, status,
                golos[0], golos[1]);
    }

    private Instant interpretarInstante(String textoIso) {
        if (textoIso == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(textoIso).toInstant();
        } catch (java.time.format.DateTimeParseException ex) {
            return Instant.parse(textoIso);
        }
    }
}
