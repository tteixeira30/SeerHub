import type { HTMLAttributes, ReactNode } from "react";

import { cn } from "@/lib/cn";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Levanta o cartão ao passar o rato — só para cartões clicáveis. */
  interactive?: boolean;
}

/** Superfície de vidro: a base de quase todos os blocos de conteúdo. */
export function Card({ interactive = false, className, children, ...props }: CardProps) {
  return (
    <div
      className={cn(
        "rounded-2xl border border-white/[0.07] bg-white/[0.025] shadow-card backdrop-blur-sm",
        interactive &&
          "transition duration-200 hover:-translate-y-0.5 hover:border-white/[0.14] hover:bg-white/[0.045] hover:shadow-lifted",
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}

/** Cabeçalho de secção dentro de um cartão (título + ação opcional à direita). */
export function CardHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-white/[0.07] px-6 py-5">
      <div>
        <h2 className="text-sm font-semibold tracking-tight text-ink-50">{title}</h2>
        {description && <p className="mt-1 text-sm text-ink-400">{description}</p>}
      </div>
      {action}
    </div>
  );
}
