package pt.seerhub.community;

import java.util.Set;

import org.junit.jupiter.api.Test;

import pt.seerhub.community.service.SlugGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/** 1c1–1c4 — geração de slug (R2, critério 1). */
class SlugGeneratorTest {

    @Test
    void baseRemoveAcentosMaiusculasEPontuacao() {
        assertThat(SlugGenerator.base("Ação & Reação!")).isEqualTo("acao-reacao");
        assertThat(SlugGenerator.base("São Paulo FC")).isEqualTo("sao-paulo-fc");
    }

    @Test
    void nomeSemCaracteresAlfanumericosCaiParaOSlugPorOmissao() {
        assertThat(SlugGenerator.base("!!! ??? ---")).isEqualTo("comunidade");
        assertThat(SlugGenerator.base("🎉🎊")).isEqualTo("comunidade");
    }

    @Test
    void gerarUnicoDevolveOSlugBaseQuandoNaoHaColisao() {
        String slug = SlugGenerator.gerarUnico("Tips do Zé", nome -> false);

        assertThat(slug).isEqualTo("tips-do-ze");
    }

    @Test
    void gerarUnicoAcrescentaSufixoNumericoAteEncontrarUmLivre() {
        Set<String> ocupados = Set.of("tips-do-ze", "tips-do-ze-2");

        String slug = SlugGenerator.gerarUnico("Tips do Zé", ocupados::contains);

        assertThat(slug).isEqualTo("tips-do-ze-3");
    }
}
