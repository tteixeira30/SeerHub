package pt.seerhub.football.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import pt.seerhub.config.SeerHubProperties;

/**
 * Cache local em disco dos emblemas de equipas e ligas (R5, critério 5).
 * Os emblemas passam a ser servidos deste diretório e nunca do CDN externo
 * em cada render.
 *
 * <p><b>Deliberadamente sem nenhuma dependência do fornecedor externo de
 * dados</b> (7b do plano de F05: só {@code FootballSyncService} depende
 * dele). É {@code FootballSyncService} quem decide se vale a pena
 * descarregar ({@link #precisaDeDescarregarEquipa}/{@link #precisaDeDescarregarLiga},
 * respeitando o limite por execução) e quem pede os bytes ao fornecedor;
 * esta classe só grava os bytes já obtidos e serve o ficheiro depois.
 *
 * <p>Limite de tamanho (512 KB) e limite de descarregamentos por execução
 * ({@code crest-downloads-per-run}, reiniciado a cada chamada a
 * {@link #reiniciarContadorDeExecucao()}), para uma sincronização de
 * catálogo não ficar bloqueada a descarregar centenas de emblemas de uma
 * só vez.
 */
@Service
public class CrestCacheService {

    private static final Logger log = LoggerFactory.getLogger(CrestCacheService.class);
    private static final long TAMANHO_MAXIMO_BYTES = 512 * 1024;
    private static final List<String> EXTENSOES_CONHECIDAS = List.of("png", "jpg", "jpeg", "svg", "webp");

    private final SeerHubProperties properties;
    private final AtomicInteger descarregamentosNestaExecucao = new AtomicInteger(0);

    public CrestCacheService(SeerHubProperties properties) {
        this.properties = properties;
    }

    /** Chamado uma vez no início de cada execução da sincronização do catálogo. */
    public void reiniciarContadorDeExecucao() {
        descarregamentosNestaExecucao.set(0);
    }

    /** {@code true} se ainda vale a pena pedir os bytes ao fornecedor para esta equipa. */
    public boolean precisaDeDescarregarEquipa(long teamId, String logoUrl) {
        return precisaDeDescarregar(logoUrl, ficheiroDaEquipa(teamId));
    }

    /** {@code true} se ainda vale a pena pedir os bytes ao fornecedor para esta liga. */
    public boolean precisaDeDescarregarLiga(long leagueId, String logoUrl) {
        return precisaDeDescarregar(logoUrl, ficheiroDaLiga(leagueId));
    }

    /** Grava os bytes (já obtidos do fornecedor por {@code FootballSyncService}) para uma equipa. */
    public void gravarEmblemaDaEquipa(long teamId, String logoUrl, byte[] conteudo) {
        gravar(caminhoDaEquipa(teamId, extensao(logoUrl)), conteudo);
    }

    /** Grava os bytes (já obtidos do fornecedor por {@code FootballSyncService}) para uma liga. */
    public void gravarEmblemaDaLiga(long leagueId, String logoUrl, byte[] conteudo) {
        gravar(caminhoDaLiga(leagueId, extensao(logoUrl)), conteudo);
    }

    public Optional<Path> ficheiroDaEquipa(long teamId) {
        return primeiroFicheiroExistente(diretorioDeEquipas(), teamId);
    }

    public Optional<Path> ficheiroDaLiga(long leagueId) {
        return primeiroFicheiroExistente(diretorioDeLigas(), leagueId);
    }

    public String urlPublicoDaEquipa(long teamId) {
        return "/api/football/crests/teams/" + teamId;
    }

    public String urlPublicoDaLiga(long leagueId) {
        return "/api/football/crests/leagues/" + leagueId;
    }

    private boolean precisaDeDescarregar(String logoUrl, Optional<Path> ficheiroExistente) {
        if (logoUrl == null || logoUrl.isBlank() || ficheiroExistente.isPresent()) {
            return false;
        }
        int limite = properties.football() != null ? properties.football().descarregamentosDeEmblemasPorExecucao() : 50;
        return descarregamentosNestaExecucao.get() < limite;
    }

    private void gravar(Path destino, byte[] conteudo) {
        if (conteudo == null) {
            return;
        }
        if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
            log.warn("Emblema para '{}' excede o limite de tamanho ({} bytes); ignorado.", destino, conteudo.length);
            return;
        }
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
            descarregamentosNestaExecucao.incrementAndGet();
        } catch (IOException ex) {
            // Nunca rebenta a sincronização por causa de um emblema — regista e continua.
            log.warn("Falha ao gravar o emblema em '{}': {}", destino, ex.getMessage());
        }
    }

    private Optional<Path> primeiroFicheiroExistente(Path diretorio, long id) {
        for (String ext : EXTENSOES_CONHECIDAS) {
            Path candidato = diretorio.resolve(id + "." + ext);
            if (Files.exists(candidato)) {
                return Optional.of(candidato);
            }
        }
        return Optional.empty();
    }

    private Path caminhoDaEquipa(long teamId, String extensao) {
        return diretorioDeEquipas().resolve(teamId + "." + extensao);
    }

    private Path caminhoDaLiga(long leagueId, String extensao) {
        return diretorioDeLigas().resolve(leagueId + "." + extensao);
    }

    private Path diretorioDeEquipas() {
        return raizDaCache().resolve("teams");
    }

    private Path diretorioDeLigas() {
        return raizDaCache().resolve("leagues");
    }

    private Path raizDaCache() {
        Path uploadsDir = properties.uploads() != null && properties.uploads().dir() != null
                ? properties.uploads().dir()
                : Path.of("./uploads");
        return uploadsDir.resolve("football").resolve("crests");
    }

    private String extensao(String url) {
        if (url == null) {
            return "png";
        }
        int ponto = url.lastIndexOf('.');
        if (ponto < 0 || ponto == url.length() - 1) {
            return "png";
        }
        String ext = url.substring(ponto + 1).toLowerCase(Locale.ROOT);
        // Corta parâmetros de query eventualmente colados à extensão.
        int interrogacao = ext.indexOf('?');
        if (interrogacao >= 0) {
            ext = ext.substring(0, interrogacao);
        }
        return ext.isBlank() ? "png" : ext;
    }
}
