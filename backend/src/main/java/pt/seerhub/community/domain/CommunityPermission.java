package pt.seerhub.community.domain;

/**
 * Catálogo fechado de ações dentro de uma comunidade (R4). Nasce completo
 * com as permissões que R7 (tips), R8 (settle), R11 (teaser) e R12 (chat)
 * vão precisar — nenhuma dessas features acrescenta valores novos (D-2 do
 * plano de F04, contrato C5 do handoff).
 *
 * <p>Cada valor carrega a sua própria {@link #mensagemDeRecusa()}, em
 * português de Portugal — é o {@code detail} devolvido pelo
 * {@code ApiException(403, ...)} quando {@link CommunityPermissionService#exigir}
 * recusa. {@code EDIT_COMMUNITY} usa literalmente o mesmo texto que
 * {@code CommunityService.MENSAGEM_SEM_PERMISSAO} já tinha antes de F04
 * (e que {@code CommunityEditIT} já assere), para essa suite continuar
 * verde sem ser tocada.
 */
public enum CommunityPermission {

    READ_TIPS("Não tem permissão para ler os tips desta comunidade."),
    READ_CHAT("Não tem permissão para ler o chat desta comunidade."),
    WRITE_CHAT("Não tem permissão para escrever no chat desta comunidade."),
    PUBLISH_TIPS("Não tem permissão para publicar tips nesta comunidade."),
    SETTLE_TIPS("Não tem permissão para resolver tips nesta comunidade."),
    DELETE_ANY_CHAT_MESSAGE("Não tem permissão para apagar mensagens de chat alheias nesta comunidade."),
    MANAGE_MODERATORS("Não tem permissão para gerir moderadores desta comunidade."),
    EDIT_COMMUNITY("Não tem permissão para editar esta comunidade."),
    DELETE_COMMUNITY("Não tem permissão para eliminar esta comunidade.");

    private final String mensagemDeRecusa;

    CommunityPermission(String mensagemDeRecusa) {
        this.mensagemDeRecusa = mensagemDeRecusa;
    }

    /** Mensagem segura para o utilizador quando esta permissão é recusada (sempre em português de Portugal). */
    public String mensagemDeRecusa() {
        return mensagemDeRecusa;
    }
}
