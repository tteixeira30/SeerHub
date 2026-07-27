package pt.seerhub.tips.parser;

/**
 * Estado de resolução do par de equipas de uma {@link ParsedSelection} contra o catálogo de
 * jogos. F06a só emite {@link #POR_RESOLVER} — os outros três valores existem aqui porque o
 * contentor ({@link TeamResolution}) é definido em F06a, mas só F06b os preenche (§2.5 do
 * plano de F06a).
 */
public enum ResolutionStatus {
    POR_RESOLVER,
    RESOLVIDA,
    AMBIGUA,
    SEM_CANDIDATOS
}
