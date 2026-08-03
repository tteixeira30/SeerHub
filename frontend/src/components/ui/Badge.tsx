import type { HTMLAttributes } from "react";

import { cn } from "@/lib/cn";
import { traduzirEstado } from "@/lib/format";

export type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

const TONES: Record<BadgeTone, string> = {
  neutral: "bg-white/[0.06] text-ink-300 ring-white/10",
  brand: "bg-brand-400/12 text-brand-300 ring-brand-400/25",
  success: "bg-emerald-400/12 text-emerald-300 ring-emerald-400/25",
  warning: "bg-amber-400/12 text-amber-300 ring-amber-400/25",
  danger: "bg-rose-500/12 text-rose-300 ring-rose-500/25",
  info: "bg-sky-400/12 text-sky-300 ring-sky-400/25",
};

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

export function Badge({ tone = "neutral", className, children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1",
        "text-[11px] font-semibold uppercase tracking-[0.08em] ring-1 ring-inset",
        TONES[tone],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
}

/**
 * Estados e papéis vindos do backend (`ACTIVE`, `OWNER`, `UP`, ...). A cor
 * é decidida aqui e o texto passa por {@link traduzirEstado}; um valor que
 * o dicionário não conheça mantém a cor neutra e o texto cru, para uma
 * novidade do backend aparecer no ecrã em vez de se perder.
 */
const TOM_POR_ESTADO: Record<string, BadgeTone> = {
  ACTIVE: "success",
  UP: "success",
  OWNER: "brand",
  MODERATOR: "info",
  MEMBER: "neutral",
  CANCELLED: "warning",
  SUSPENDED: "warning",
  EXPIRED: "danger",
  DOWN: "danger",
};

export function StatusBadge({
  value,
  className,
  testId,
}: {
  value: string;
  className?: string;
  testId?: string;
}) {
  const tone = TOM_POR_ESTADO[value] ?? "neutral";
  return (
    <Badge tone={tone} className={className} data-testid={testId}>
      <span
        className={cn(
          "h-1.5 w-1.5 rounded-full bg-current",
          tone === "success" && "animate-pulse"
        )}
        aria-hidden="true"
      />
      {traduzirEstado(value)}
    </Badge>
  );
}
