package pt.seerhub.tips.domain;

/**
 * Espelho exato do {@code CHECK ck_tip_imports_status} de {@code V2__baseline_schema.sql}.
 * F06a deriva este valor a partir do {@code ParseResult} ({@link
 * pt.seerhub.tips.parser.ParseResult#estado()}); F07 é quem persiste um {@code TipImport} com
 * ele.
 */
public enum ImportStatus {
    OK,
    PARTIAL,
    FAILED
}
