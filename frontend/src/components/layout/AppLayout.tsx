import { useEffect, useState, type ReactNode } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";

import { Brand } from "@/components/layout/Brand";
import { Avatar } from "@/components/ui/Avatar";
import {
  IconAccount,
  IconClose,
  IconCommunities,
  IconLogout,
  IconMenu,
  IconSubscriptions,
} from "@/components/ui/Icons";
import { cn } from "@/lib/cn";
import { useAuth } from "@/lib/auth";

interface Entrada {
  to: string;
  label: string;
  icon: ReactNode;
  /** Só marca como ativa em correspondência exata (evita `/comunidades` acesa em `/comunidades/nova`). */
  end?: boolean;
}

const NAVEGACAO: Entrada[] = [
  { to: "/comunidades", label: "As minhas comunidades", icon: <IconCommunities className="h-[1.15rem] w-[1.15rem]" /> },
  { to: "/subscricoes", label: "Subscrições", icon: <IconSubscriptions className="h-[1.15rem] w-[1.15rem]" /> },
  { to: "/conta", label: "Conta", icon: <IconAccount className="h-[1.15rem] w-[1.15rem]" /> },
];

function ItemDeNavegacao({ entrada, onNavigate }: { entrada: Entrada; onNavigate?: () => void }) {
  return (
    <NavLink
      to={entrada.to}
      end={entrada.end}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          "group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition duration-200",
          isActive
            ? "bg-white/[0.07] font-medium text-white"
            : "text-ink-300 hover:bg-white/[0.04] hover:text-ink-50"
        )
      }
    >
      {({ isActive }) => (
        <>
          <span
            className={cn(
              "absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-r-full bg-brand-400 transition-opacity",
              isActive ? "opacity-100" : "opacity-0"
            )}
            aria-hidden="true"
          />
          <span className={cn("transition", isActive ? "text-brand-400" : "text-ink-400 group-hover:text-ink-200")}>
            {entrada.icon}
          </span>
          {entrada.label}
        </>
      )}
    </NavLink>
  );
}

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { utilizador, sair } = useAuth();
  const navigate = useNavigate();

  async function terminarSessao() {
    await sair();
    navigate("/entrar", { replace: true });
  }

  return (
    <div className="flex h-full flex-col gap-8 px-4 py-6">
      <div className="px-2">
        <Brand />
      </div>

      <nav className="flex-1 space-y-1" aria-label="Navegação principal">
        {NAVEGACAO.map((entrada) => (
          <ItemDeNavegacao key={entrada.to} entrada={entrada} onNavigate={onNavigate} />
        ))}
      </nav>

      {utilizador && (
        <div className="rounded-2xl border border-white/[0.07] bg-white/[0.02] p-3">
          <div className="flex items-center gap-3">
            <Avatar name={utilizador.displayName} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink-100">{utilizador.displayName}</p>
              <p className="truncate text-xs text-ink-400">{utilizador.email}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void terminarSessao()}
            className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-ink-400 transition hover:bg-white/5 hover:text-ink-100"
          >
            <IconLogout className="h-4 w-4" />
            Terminar sessão
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Moldura das páginas autenticadas: barra lateral fixa a partir de `lg` e
 * gaveta deslizante em ecrãs pequenos. Fecha-se sozinha a cada mudança de
 * rota, para o utilizador não ficar com o menu aberto por cima do destino.
 */
export function AppLayout() {
  const [menuAberto, setMenuAberto] = useState(false);
  const location = useLocation();

  useEffect(() => {
    setMenuAberto(false);
  }, [location.pathname]);

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[17rem_1fr]">
      <aside className="sticky top-0 hidden h-screen border-r border-white/[0.06] bg-ink-950/40 backdrop-blur-xl lg:block">
        <Sidebar />
      </aside>

      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-white/[0.06] bg-ink-950/80 px-4 py-3 backdrop-blur-xl lg:hidden">
        <Brand />
        <button
          type="button"
          onClick={() => setMenuAberto(true)}
          className="rounded-lg p-2 text-ink-300 transition hover:bg-white/5 hover:text-ink-50"
          aria-label="Abrir menu"
          aria-expanded={menuAberto}
        >
          <IconMenu className="h-5 w-5" />
        </button>
      </header>

      {menuAberto && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            className="absolute inset-0 h-full w-full cursor-default bg-ink-950/70 backdrop-blur-sm"
            onClick={() => setMenuAberto(false)}
            aria-label="Fechar menu"
          />
          <div className="absolute inset-y-0 left-0 w-72 max-w-[85vw] animate-fade-up border-r border-white/[0.08] bg-ink-950 shadow-lifted">
            <button
              type="button"
              onClick={() => setMenuAberto(false)}
              className="absolute right-3 top-6 rounded-lg p-2 text-ink-400 transition hover:bg-white/5 hover:text-ink-50"
              aria-label="Fechar menu"
            >
              <IconClose className="h-5 w-5" />
            </button>
            <Sidebar onNavigate={() => setMenuAberto(false)} />
          </div>
        </div>
      )}

      <main className="min-w-0 px-4 py-8 sm:px-8 lg:py-12">
        <div className="mx-auto max-w-5xl animate-fade-up">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
