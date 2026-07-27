import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { clearSession } from "@/lib/api";
import { AuthProvider, useAuth } from "@/lib/auth";

const CHAVE_REFRESH_TOKEN = "seerhub.refreshToken";

function Sonda() {
  const { aCarregar, autenticado, utilizador } = useAuth();
  if (aCarregar) {
    return <p>a-carregar</p>;
  }
  return <p>{autenticado ? `autenticado:${utilizador?.email}` : "visitante"}</p>;
}

describe("AuthProvider", () => {
  beforeEach(() => {
    clearSession();
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("arrancaComoVisitanteQuandoNaoHaRefreshTokenGuardado", async () => {
    render(
      <AuthProvider>
        <Sonda />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("visitante")).toBeInTheDocument();
    });
  });

  it("retomaASessaoAutomaticamenteQuandoHaRefreshTokenGuardado", async () => {
    localStorage.setItem(CHAVE_REFRESH_TOKEN, "refresh-existente");

    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            accessToken: "novo-token",
            refreshToken: "novo-refresh",
            tokenType: "Bearer",
            expiresIn: 900,
            user: {
              id: 7,
              email: "existente@exemplo.pt",
              username: "existente",
              displayName: "Existente",
              globalRole: "USER",
              status: "ACTIVE",
              createdAt: "2026-01-01T00:00:00Z",
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
    );

    render(
      <AuthProvider>
        <Sonda />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("autenticado:existente@exemplo.pt")).toBeInTheDocument();
    });
  });
});
