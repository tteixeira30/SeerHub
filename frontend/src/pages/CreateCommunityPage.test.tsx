import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { CreateCommunityPage } from "@/pages/CreateCommunityPage";

function renderizar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/comunidades/nova"]}>
        <Routes>
          <Route path="/comunidades/nova" element={<CreateCommunityPage />} />
          <Route path="/comunidades/:slug/definicoes" element={<p>Definições da comunidade</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CreateCommunityPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("criarComunidadeComNomeValidoNavegaParaAsDefinicoes", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url, init) => {
        expect(init?.method).toBe("POST");
        return new Response(
          JSON.stringify({
            id: 1,
            slug: "tips-do-ze",
            name: "Tips do Zé",
            priceMonthlyCents: 0,
            currency: "EUR",
            status: "ACTIVE",
            ownerId: 1,
            ownerDisplayName: "Zé",
            createdAt: "2026-01-01T00:00:00Z",
            ownedByViewer: true,
          }),
          { status: 201, headers: { "Content-Type": "application/json" } }
        );
      })
    );

    renderizar();

    fireEvent.change(screen.getByLabelText("Nome"), { target: { value: "Tips do Zé" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar comunidade" }));

    await waitFor(() => {
      expect(screen.getByText("Definições da comunidade")).toBeInTheDocument();
    });

    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/communities"), expect.anything());
  });

  it("limiteDeComunidadesMostraAMensagemDevolvidaPeloBackend", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            detail: "Atingiu o limite de 3 comunidades ativas. Suspenda ou aguarde antes de criar outra.",
          }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        )
      )
    );

    renderizar();

    fireEvent.change(screen.getByLabelText("Nome"), { target: { value: "Comunidade Quatro" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar comunidade" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Atingiu o limite de 3 comunidades ativas. Suspenda ou aguarde antes de criar outra."
    );
    expect(screen.queryByText("Definições da comunidade")).not.toBeInTheDocument();
  });
});
