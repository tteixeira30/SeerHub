import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

export type AlertTone = "error" | "success" | "warning" | "info";

const TONES: Record<AlertTone, string> = {
  error: "border-rose-500/25 bg-rose-500/[0.08] text-rose-200",
  success: "border-emerald-400/25 bg-emerald-400/[0.08] text-emerald-200",
  warning: "border-amber-400/25 bg-amber-400/[0.08] text-amber-200",
  info: "border-sky-400/25 bg-sky-400/[0.08] text-sky-200",
};

/**
 * Mensagem de estado. Só o tom `error` é `role="alert"` (interrompe o leitor
 * de ecrã); confirmações usam `role="status"`, que espera pela pausa.
 */
export function Alert({
  tone = "error",
  className,
  children,
}: {
  tone?: AlertTone;
  className?: string;
  children: ReactNode;
}) {
  return (
    <p
      role={tone === "error" ? "alert" : "status"}
      className={cn(
        "animate-fade-up rounded-xl border px-4 py-3 text-sm",
        TONES[tone],
        className
      )}
    >
      {children}
    </p>
  );
}
