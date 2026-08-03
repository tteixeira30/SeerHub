/** Junta classes CSS ignorando `false`/`null`/`undefined`. Sem dependências. */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
}
