/**
 * Formatação para apresentação. Deliberadamente sem `Intl` nem `Date`: as
 * datas do backend chegam em ISO-8601 e são lidas pelo prefixo
 * `YYYY-MM-DD`, para o que aparece no ecrã não depender do fuso horário
 * nem da locale do browser (o mesmo instante nunca muda de dia).
 */

/** `"2026-08-26T12:00:00Z"` → `"26/08/2026"`. Devolve o original se não reconhecer. */
export function formatarData(iso: string | null | undefined): string {
  if (!iso) {
    return "";
  }
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!partes) {
    return iso;
  }
  const [, ano, mes, dia] = partes;
  return `${dia}/${mes}/${ano}`;
}

/** Iniciais para o avatar quando não há imagem: `"Tips do Zé"` → `"TZ"`. */
export function iniciais(nome: string): string {
  const palavras = nome
    .trim()
    .split(/\s+/)
    .filter((palavra) => palavra.length > 0 && /\p{L}/u.test(palavra[0]));

  if (palavras.length === 0) {
    return "?";
  }
  if (palavras.length === 1) {
    return palavras[0].slice(0, 2).toUpperCase();
  }
  return (palavras[0][0] + palavras[palavras.length - 1][0]).toUpperCase();
}
