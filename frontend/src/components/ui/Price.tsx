import { cn } from "@/lib/cn";
import { centimosParaEuros } from "@/lib/communities";

const SIMBOLOS: Record<string, string> = { EUR: "€", USD: "$", GBP: "£" };

/** `EUR` → `€`; qualquer outra moeda mostra o próprio código. */
export function simboloMoeda(currency: string): string {
  return SIMBOLOS[currency] ?? currency;
}

/**
 * Preço mensal. Gratuito (0 cêntimos) é dito por palavras — mostrar
 * "0,00 €/mês" faria o leitor procurar o truque.
 */
export function Price({
  cents,
  currency,
  size = "md",
  className,
}: {
  cents: number;
  currency: string;
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const tamanhos = {
    sm: "text-sm",
    md: "text-base",
    lg: "text-2xl",
  } as const;

  if (cents === 0) {
    return (
      <span className={cn("font-semibold text-brand-300", tamanhos[size], className)}>
        Gratuito
      </span>
    );
  }

  return (
    <span className={cn("inline-flex items-baseline gap-1 text-ink-50", className)}>
      <span className={cn("font-semibold tabular tracking-tight", tamanhos[size])}>
        {centimosParaEuros(cents)} {simboloMoeda(currency)}
      </span>
      <span className="text-xs font-normal text-ink-400">/mês</span>
    </span>
  );
}
