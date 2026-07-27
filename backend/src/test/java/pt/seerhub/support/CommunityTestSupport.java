package pt.seerhub.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pt.seerhub.community.api.CommunityResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Utilitário partilhado de comunidades para os {@code *IT} de F02. Classe
 * simples, sem anotações Spring — construída explicitamente em
 * {@code @BeforeEach}, mesmo padrão de {@link AuthTestSupport}.
 *
 * <p>{@link #inserirMembership} escreve diretamente por JDBC,
 * <b>deliberadamente</b>: F02 não tem endpoint de subscrição nem de
 * nomeação de moderador (§2.1 do plano) — inserir uma linha
 * {@code MEMBER}/{@code MODERATOR} por API inventaria a superfície de
 * F03/F04, que este teste não pode antecipar.
 */
public class CommunityTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public CommunityTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Nome único por teste (contentor Postgres partilhado, slug UNIQUE global). Sempre ≤60 caracteres. */
    public String nomeUnico(String prefixo) {
        String candidato = prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
        return candidato.length() > 60 ? candidato.substring(0, 60) : candidato;
    }

    public CommunityResponse criar(String accessToken, String nome) throws Exception {
        return criar(accessToken, nome, 0);
    }

    public CommunityResponse criar(String accessToken, String nome, int precoCentimos) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", nome);
        corpo.put("priceMonthlyCents", precoCentimos);

        MvcResult resultado = mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(resultado.getResponse().getContentAsString(), CommunityResponse.class);
    }

    /** Suspende uma comunidade por UPDATE direto — não há UI de admin antes de F14. */
    public void suspender(String slug) {
        jdbcTemplate.update("UPDATE communities SET status = 'SUSPENDED' WHERE slug = ?", slug);
    }

    public void reativar(String slug) {
        jdbcTemplate.update("UPDATE communities SET status = 'ACTIVE' WHERE slug = ?", slug);
    }

    /**
     * Insere uma linha crua em {@code community_memberships} — deliberadamente
     * fora da API (ver nota da classe). {@code expiresAt} pode ser
     * {@code null}.
     */
    public void inserirMembership(long communityId, long userId, String role, String status, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO community_memberships (community_id, user_id, role, status, joined_at, expires_at, version) "
                        + "VALUES (?, ?, ?, ?, now(), ?, 0)",
                communityId, userId, role, status, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
    }

    /** Fotografia de uma linha de membership, coluna a coluna (usada no critério 4). */
    public Map<String, Object> lerMembership(long communityId, long userId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, community_id, user_id, role, status, joined_at, expires_at, version "
                        + "FROM community_memberships WHERE community_id = ? AND user_id = ?",
                communityId, userId);
    }

    public long contarMemberships(long communityId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_memberships WHERE community_id = ?", Long.class, communityId);
        return total == null ? 0 : total;
    }

    public Object lerColunaDaComunidade(String slug, String coluna) {
        return jdbcTemplate.queryForObject(
                "SELECT " + coluna + " FROM communities WHERE slug = ?", Object.class, slug);
    }
}
