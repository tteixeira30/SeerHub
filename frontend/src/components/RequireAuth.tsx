import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";

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
    return <p>A verificar sessão...</p>;
  }

  if (!autenticado) {
    return <Navigate to="/entrar" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
