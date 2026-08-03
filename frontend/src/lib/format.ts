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

/**
 * Estados e papéis do backend em português de Portugal.
 *
 * As chaves são os enumerados tal como chegam na resposta
 * (`MembershipStatus`, `MembershipRole`, `CommunityStatus`, `UserStatus`,
 * `GlobalRole` e o estado do Actuator). Os estados descrevem sempre um
 * nome feminino — a comunidade, a subscrição, a conta —, daí "Ativa" e
 * não "Ativo".
 */
const ESTADOS: Record<string, string> = {
  ACTIVE: "Ativa",
  CANCELLED: "Cancelada",
  EXPIRED: "Expirada",
  SUSPENDED: "Suspensa",
  OWNER: "Dono",
  MODERATOR: "Moderador",
  MEMBER: "Membro",
  USER: "Utilizador",
  ADMIN: "Administrador",
  UP: "Operacional",
  DOWN: "Indisponível",
};

/**
 * Um valor que ainda não esteja no dicionário é mostrado como veio, em vez
 * de desaparecer do ecrã: um enumerado novo no backend fica visível (e
 * feio, de propósito) até alguém o traduzir aqui.
 */
export function traduzirEstado(valor: string): string {
  return ESTADOS[valor] ?? valor;
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
