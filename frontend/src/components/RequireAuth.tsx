import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";

import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/lib/auth";

/**
 * Envolve rotas protegidas: redireciona para {@code /entrar} (com
 * {@code state.from} para regressar ao destino original depois do login)
 * quando não há sessão. Enquanto a tentativa inicial de retomar sessão
 * decorre, mostra um estado de carregamento em vez de redirecionar cedo
 * demais (senão um refresh de página perderia sempre a sessão).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { autenticado, aCarregar } = useAuth();
  const location = useLocation();

  if (aCarregar) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4">
        <Spinner className="h-7 w-7 text-brand-400" />
        <p className="text-sm text-ink-400">A verificar sessão...</p>
      </div>
    );
  }

  if (!autenticado) {
    return <Navigate to="/entrar" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
