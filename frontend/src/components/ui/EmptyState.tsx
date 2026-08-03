import type { ReactNode } from "react";

/** Lista vazia: ícone, uma frase que explica porquê e o próximo passo. */
export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-white/10 bg-white/[0.015] px-6 py-16 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-400/10 text-brand-300 ring-1 ring-inset ring-brand-400/20">
        {icon}
      </div>
      <h2 className="mt-5 text-base font-semibold text-ink-50">{title}</h2>
      <p className="mt-2 max-w-sm text-sm text-ink-400">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
