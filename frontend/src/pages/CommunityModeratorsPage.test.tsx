import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { CommunityModeratorsPage } from "@/pages/CommunityModeratorsPage";

const SLUG = "tips-do-ze";
const DONO = { userId: 1, username: "ze", displayName: "Zé", role: "OWNER", status: "ACTIVE", joinedAt: "2026-01-01T00:00:00Z", expiresAt: null, active: true };

function jsonResponse(corpo: unknown, status = 200) {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderizar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/comunidades/${SLUG}/moderadores`]}>
        <Routes>
          <Route path="/comunidades/:slug/moderadores" element={<CommunityModeratorsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CommunityModeratorsPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("donoVeOsMembrosENomeiaModerador", async () => {
    const membro = { userId: 2, username: "ana", displayName: "Ana", role: "MEMBER" as "MEMBER" | "MODERATOR", status: "ACTIVE", joinedAt: "2026-02-01T00:00:00Z", expiresAt: "2026-08-01T00:00:00Z", active: true };

    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const metodo = init?.method ?? "GET";

        if (url.endsWith(`/api/communities/${SLUG}/moderators`) && metodo === "POST") {
          membro.role = "MODERATOR";
          return jsonResponse({ ...membro });
        }

        if (url.endsWith(`/api/communities/${SLUG}/members`) && metodo === "GET") {
          return jsonResponse([DONO, membro]);
        }

        throw new Error(`URL não esperado no mock de fetch: ${metodo} ${url}`);
      })
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByText("Ana")).toBeInTheDocument();
    });
    expect(screen.getByTestId("papel-2")).toHaveTextContent("Membro");

    fireEvent.click(screen.getByRole("button", { name: "Nomear moderador" }));

    await waitFor(() => {
      expect(screen.getByTestId("papel-2")).toHaveTextContent("Moderador");
    });
    expect(screen.getByRole("button", { name: "Remover moderador" })).toBeInTheDocument();
  });

  it("removerModeradorVoltaAMostrarComoMembro", async () => {
    const moderador = { userId: 3, username: "rui", displayName: "Rui", role: "MODERATOR" as "MEMBER" | "MODERATOR", status: "ACTIVE", joinedAt: "2026-02-01T00:00:00Z", expiresAt: "2026-08-01T00:00:00Z", active: true };

    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const metodo = init?.method ?? "GET";

        if (url.endsWith(`/api/communities/${SLUG}/moderators/3`) && metodo === "DELETE") {
          moderador.role = "MEMBER";
          return jsonResponse({ ...moderador });
        }

        if (url.endsWith(`/api/communities/${SLUG}/members`) && metodo === "GET") {
          return jsonResponse([DONO, moderador]);
        }

        throw new Error(`URL não esperado no mock de fetch: ${metodo} ${url}`);
      })
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByText("Rui")).toBeInTheDocument();
    });
    expect(screen.getByTestId("papel-3")).toHaveTextContent("Moderador");

    fireEvent.click(screen.getByRole("button", { name: "Remover moderador" }));

    await waitFor(() => {
      expect(screen.getByTestId("papel-3")).toHaveTextContent("Membro");
    });
    expect(screen.getByRole("button", { name: "Nomear moderador" })).toBeInTheDocument();
  });

  it("respostaDe403MostraAMensagemDoServidorESemBotoes", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith(`/api/communities/${SLUG}/members`)) {
          return jsonResponse({ detail: "Não tem permissão para gerir moderadores desta comunidade." }, 403);
        }
        throw new Error(`URL não esperado no mock de fetch: ${url}`);
      })
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Não tem permissão para gerir moderadores desta comunidade."
      );
    });
    expect(screen.queryByRole("button", { name: "Nomear moderador" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remover moderador" })).not.toBeInTheDocument();
  });
});
