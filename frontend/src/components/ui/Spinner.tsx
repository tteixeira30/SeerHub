import { cn } from "@/lib/cn";

/** Indicador de progresso decorativo: o texto ao lado é que descreve o estado. */
export function Spinner({ className }: { className?: string }) {
  return (
    <svg
      className={cn("animate-spin", className)}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path
        d="M21 12a9 9 0 0 0-9-9"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  );
}

/** Estado de carregamento de página inteira, centrado. */
export function LoadingScreen({ label }: { label: string }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-ink-400">
      <Spinner className="h-7 w-7 text-brand-400" />
      <p className="text-sm">{label}</p>
    </div>
  );
}
