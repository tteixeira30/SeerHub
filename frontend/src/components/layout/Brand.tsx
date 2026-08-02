import { Link } from "react-router-dom";

import { cn } from "@/lib/cn";

/**
 * Marca do SeerHub: símbolo + logótipo em duas cores.
 *
 * O texto está deliberadamente partido em dois nós (`Seer` + `Hub`) — o
 * `<h1>SeerHub</h1>` da página inicial é que fica com o nome inteiro num
 * único nó de texto, para haver sempre um e um só título da aplicação.
 */
export function Brand({ to = "/", className }: { to?: string; className?: string }) {
  return (
    <Link
      to={to}
      className={cn("group inline-flex items-center gap-2.5 rounded-lg", className)}
      aria-label="SeerHub"
    >
      <span className="relative flex h-8 w-8 items-center justify-center rounded-[0.6rem] bg-gradient-to-br from-brand-400 to-brand-600 shadow-brand transition group-hover:scale-105">
        <svg viewBox="0 0 24 24" className="h-[1.125rem] w-[1.125rem] text-ink-950" aria-hidden="true">
          <path
            d="M12 4.5c4.2 0 7.6 2.6 9 7.5-1.4 4.9-4.8 7.5-9 7.5S4.4 16.9 3 12c1.4-4.9 4.8-7.5 9-7.5Z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinejoin="round"
          />
          <circle cx="12" cy="12" r="2.6" fill="currentColor" />
        </svg>
      </span>
      <span className="text-[1.0625rem] font-semibold tracking-tight text-ink-50">
        Seer<span className="text-brand-400">Hub</span>
      </span>
    </Link>
  );
}
