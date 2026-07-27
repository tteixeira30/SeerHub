package pt.seerhub.community;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.service.CommunityPermissionRules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A matriz pura papel × permissão (R4, §3.1 do plano de F04). Sem Spring,
 * sem base de dados — testa só {@link CommunityPermissionRules}.
 */
class CommunityPermissionRulesTest {

    @Test
    void moderadorPodePublicarEResolverTips() {
        assertThat(CommunityPermissionRules.permite(MembershipRole.MODERATOR, false, CommunityPermission.PUBLISH_TIPS))
                .isTrue();
        assertThat(CommunityPermissionRules.permite(MembershipRole.MODERATOR, false, CommunityPermission.SETTLE_TIPS))
                .isTrue();
    }

    @Test
    void moderadorPodeApagarMensagensDeChatDeOutros() {
        assertThat(CommunityPermissionRules.permite(
                MembershipRole.MODERATOR, false, CommunityPermission.DELETE_ANY_CHAT_MESSAGE))
                .isTrue();
    }

    @Test
    void moderadorNaoPodeApagarAComunidade() {
        assertThat(CommunityPermissionRules.permite(MembershipRole.MODERATOR, true, CommunityPermission.DELETE_COMMUNITY))
                .isFalse();
    }

    @Test
    void membroAtivoPodeLerTipsELerEEscreverNoChat() {
        Set<CommunityPermission> permissoes = CommunityPermissionRules.permissoesDe(MembershipRole.MEMBER, true);
        assertThat(permissoes).contains(
                CommunityPermission.READ_TIPS, CommunityPermission.READ_CHAT, CommunityPermission.WRITE_CHAT);
    }

    @Test
    void membroAtivoNaoPodePublicarNemResolverTips() {
        Set<CommunityPermission> permissoes = CommunityPermissionRules.permissoesDe(MembershipRole.MEMBER, true);
        assertThat(permissoes).doesNotContain(CommunityPermission.PUBLISH_TIPS, CommunityPermission.SETTLE_TIPS);
    }

    @Test
    void membroSemAcessoPremiumPerdeLeituraEEscrita() {
        assertThat(CommunityPermissionRules.permissoesDe(MembershipRole.MEMBER, false)).isEmpty();
    }

    @Test
    void papelNuloOuDesconhecidoFechaPorOmissao() {
        assertThat(CommunityPermissionRules.permissoesDe(null, true)).isEmpty();
        assertThat(CommunityPermissionRules.permissoesDe(null, false)).isEmpty();
    }

    @Test
    void visitanteSemPapelNaoTemNenhumaPermissao() {
        for (CommunityPermission permissao : CommunityPermission.values()) {
            assertThat(CommunityPermissionRules.permite(null, false, permissao)).isFalse();
            assertThat(CommunityPermissionRules.permite(null, true, permissao)).isFalse();
        }
    }

    @Test
    void todaAPermissaoTemMensagemDeRecusaEmPortugues() {
        for (CommunityPermission permissao : CommunityPermission.values()) {
            assertThat(permissao.mensagemDeRecusa()).isNotBlank();
        }
    }

    @Test
    void donoTemTodasAsPermissoesDaComunidade() {
        Set<CommunityPermission> permissoes = CommunityPermissionRules.permissoesDe(MembershipRole.OWNER, false);
        assertThat(permissoes).containsExactlyInAnyOrder(CommunityPermission.values());

        // O dono ignora o acesso premium tal como o moderador (D-5 de F03).
        Set<CommunityPermission> permissoesSemPremium = CommunityPermissionRules.permissoesDe(MembershipRole.OWNER, false);
        assertThat(permissoesSemPremium).containsExactlyInAnyOrder(CommunityPermission.values());
    }

    /**
     * A tabela literal do §3.1 do plano: 9 permissões × 5 estados de papel
     * = 45 células, todas asseridas uma a uma. Qualquer célula errada, ou
     * um valor de enum futuro sem linha aqui, rebenta este teste.
     */
    @Test
    void aMatrizCompletaDePapelPorPermissaoEExatamenteAEsperada() {
        Map<CommunityPermission, boolean[]> esperado = new EnumMap<>(CommunityPermission.class);
        // { OWNER, MODERATOR, MEMBER com premium, MEMBER sem premium, sem papel }
        esperado.put(CommunityPermission.READ_TIPS, new boolean[] { true, true, true, false, false });
        esperado.put(CommunityPermission.READ_CHAT, new boolean[] { true, true, true, false, false });
        esperado.put(CommunityPermission.WRITE_CHAT, new boolean[] { true, true, true, false, false });
        esperado.put(CommunityPermission.PUBLISH_TIPS, new boolean[] { true, true, false, false, false });
        esperado.put(CommunityPermission.SETTLE_TIPS, new boolean[] { true, true, false, false, false });
        esperado.put(CommunityPermission.DELETE_ANY_CHAT_MESSAGE, new boolean[] { true, true, false, false, false });
        esperado.put(CommunityPermission.MANAGE_MODERATORS, new boolean[] { true, false, false, false, false });
        esperado.put(CommunityPermission.EDIT_COMMUNITY, new boolean[] { true, false, false, false, false });
        esperado.put(CommunityPermission.DELETE_COMMUNITY, new boolean[] { true, false, false, false, false });

        assertThat(esperado).hasSize(9);

        for (Map.Entry<CommunityPermission, boolean[]> entrada : esperado.entrySet()) {
            CommunityPermission permissao = entrada.getKey();
            boolean[] valores = entrada.getValue();

            assertThat(CommunityPermissionRules.permite(MembershipRole.OWNER, true, permissao))
                    .as("%s para OWNER", permissao).isEqualTo(valores[0]);
            assertThat(CommunityPermissionRules.permite(MembershipRole.MODERATOR, true, permissao))
                    .as("%s para MODERATOR", permissao).isEqualTo(valores[1]);
            assertThat(CommunityPermissionRules.permite(MembershipRole.MEMBER, true, permissao))
                    .as("%s para MEMBER com premium", permissao).isEqualTo(valores[2]);
            assertThat(CommunityPermissionRules.permite(MembershipRole.MEMBER, false, permissao))
                    .as("%s para MEMBER sem premium", permissao).isEqualTo(valores[3]);
            assertThat(CommunityPermissionRules.permite(null, true, permissao))
                    .as("%s sem papel", permissao).isEqualTo(valores[4]);
        }
    }
}
