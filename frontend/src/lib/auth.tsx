import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

import { apiFetch, clearSession, getRefreshToken, setSession, type AuthResponseBody } from "@/lib/api";

/** Espelha `UserResponse` do backend (nunca traz a password). */
export interface Utilizador {
  id: number;
  email: string;
  username: string;
  displayName: string;
  globalRole: string;
  status: string;
  createdAt: string;
}

interface AuthContextValue {
  utilizador: Utilizador | null;
  autenticado: boolean;
  /** Verdadeiro enquanto a tentativa inicial de retomar sessão (refresh) decorre. */
  aCarregar: boolean;
  registar: (email: string, password: string, displayName?: string) => Promise<void>;
  entrar: (email: string, password: string) => Promise<void>;
  sair: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * Estado de sessão do SeerHub (F01). Ao montar, se houver refresh token em
 * `localStorage`, tenta renovar a sessão uma vez (`/api/auth/refresh`) para
 * que recarregar a página não obrigue a novo login. Se falhar, começa por
 * omissão como visitante — nunca lança nem bloqueia a aplicação.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [utilizador, setUtilizador] = useState<Utilizador | null>(null);
  const [aCarregar, setACarregar] = useState(true);

  useEffect(() => {
    let cancelado = false;

    async function tentarRetomarSessao() {
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        if (!cancelado) {
          setACarregar(false);
        }
        return;
      }

      try {
        const resposta = await apiFetch<AuthResponseBody>("/api/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        if (!cancelado) {
          setSession(resposta.accessToken, resposta.refreshToken);
          setUtilizador(resposta.user as Utilizador);
        }
      } catch {
        if (!cancelado) {
          clearSession();
        }
      } finally {
        if (!cancelado) {
          setACarregar(false);
        }
      }
    }

    void tentarRetomarSessao();

    return () => {
      cancelado = true;
    };
  }, []);

  const registar = useCallback(async (email: string, password: string, displayName?: string) => {
    const corpo: Record<string, string> = { email, password };
    if (displayName) {
      corpo.displayName = displayName;
    }

    const resposta = await apiFetch<AuthResponseBody>("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(corpo),
    });
    setSession(resposta.accessToken, resposta.refreshToken);
    setUtilizador(resposta.user as Utilizador);
  }, []);

  const entrar = useCallback(async (email: string, password: string) => {
    const resposta = await apiFetch<AuthResponseBody>("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    setSession(resposta.accessToken, resposta.refreshToken);
    setUtilizador(resposta.user as Utilizador);
  }, []);

  const sair = useCallback(async () => {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        await apiFetch("/api/auth/logout", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
      }
    } finally {
      clearSession();
      setUtilizador(null);
    }
  }, []);

  const valor: AuthContextValue = {
    utilizador,
    autenticado: utilizador !== null,
    aCarregar,
    registar,
    entrar,
    sair,
  };

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const contexto = useContext(AuthContext);
  if (!contexto) {
    throw new Error("useAuth só pode ser usado dentro de um <AuthProvider>.");
  }
  return contexto;
}
