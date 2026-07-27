package pt.seerhub.football;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.service.CrestCacheService;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.FootballTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R5, critérios 5d-5h — cache local de emblemas, servida do disco. */
class CrestCacheIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FootballSyncService footballSyncService;
    @Autowired
    private FixedFootballDataProvider fake;
    @Autowired
    private CrestCacheService crestCacheService;

    private FootballTestSupport footballTestSupport;
    private final AtomicLong contadorId = new AtomicLong(66_000_000L + (System.nanoTime() % 90_000L));

    @BeforeEach
    void setUp() {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        footballTestSupport.limparOrcamento();
        fake.limparRegisto();
    }

    @Test
    void aSincronizacaoDescarregaOsEmblemasParaACacheLocal() throws Exception {
        footballSyncService.sincronizarCatalogo();

        Map<String, Object> equipa = footballTestSupport.lerEquipaPorProviderId(940101L);
        long teamId = ((Number) equipa.get("id")).longValue();

        var ficheiro = crestCacheService.ficheiroDaEquipa(teamId);
        assertThat(ficheiro).isPresent();
        assertThat(Files.exists(ficheiro.get())).isTrue();
        assertThat(Files.size(ficheiro.get())).isGreaterThan(0);
    }

    @Test
    void oEmblemaEServidoDoDiscoComCabecalhosDeCache() throws Exception {
        footballSyncService.sincronizarCatalogo();
        Map<String, Object> equipa = footballTestSupport.lerEquipaPorProviderId(940101L);
        long teamId = ((Number) equipa.get("id")).longValue();

        MvcResult resultado = mockMvc.perform(get("/api/football/crests/teams/{id}", teamId))
                .andExpect(status().isOk())
                .andReturn();

        String cacheControl = resultado.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
        assertThat(cacheControl).contains("public").contains("max-age=604800");
        assertThat(resultado.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void oDescarregamentoDeEmblemasNaoConsomeOrcamento() {
        footballSyncService.sincronizarCatalogo();

        long chamadasDeDados = fake.chamadas().size();
        int orcamentoConsumido = footballTestSupport.consumoDoDia();

        // O orçamento só conta chamadas de dados (JOGOS_ENTRE_DATAS/JOGOS_DO_DIA/EQUIPAS_DA_LIGA);
        // os ~22 emblemas descarregados nesta sincronização (16 equipas + 6 ligas) não entram.
        assertThat(orcamentoConsumido).isEqualTo((int) chamadasDeDados);
    }

    @Test
    void emblemaAindaSemCacheRedirecionaParaAOrigemENuncaFalha() throws Exception {
        long providerId = contadorId.incrementAndGet();
        String logoUrl = "https://fake-provider.test/logos/team-" + providerId + ".png";
        long teamId = footballTestSupport.inserirEquipaComEmblema(providerId, "Equipa Sem Cache", "equipa sem cache", logoUrl);

        // A cache em disco vive num diretório persistente do SO (tmpdir), que sobrevive
        // entre execuções separadas de "mvn test" (ao contrário do Postgres do
        // Testcontainer, que é sempre fresco). Se uma execução anterior já tiver
        // gravado um ficheiro para este mesmo id de base de dados (a sequência de
        // identidade também recomeça em 1 a cada execução fresca), o teste teria um
        // falso positivo — por isso apaga-se explicitamente antes de verificar.
        crestCacheService.ficheiroDaEquipa(teamId).ifPresent(caminho -> {
            try {
                Files.deleteIfExists(caminho);
            } catch (java.io.IOException ignored) {
                // Falha a apagar não deve derrubar o teste — a asserção seguinte revela o problema.
            }
        });

        assertThat(crestCacheService.ficheiroDaEquipa(teamId)).isEmpty();

        MvcResult resultado = mockMvc.perform(get("/api/football/crests/teams/{id}", teamId))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(resultado.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(logoUrl);
    }

    @Test
    void emblemaDeEquipaDesconhecidaDevolve404EmProblemDetail() throws Exception {
        long idInexistente = 999_999_999L;

        mockMvc.perform(get("/api/football/crests/teams/{id}", idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.detail").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.correlationId").exists());
    }
}
