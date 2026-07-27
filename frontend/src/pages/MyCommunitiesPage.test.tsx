import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { MyCommunitiesPage } from "@/pages/MyCommunitiesPage";

function renderizar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/comunidades"]}>
        <Routes>
          <Route path="/comunidades" element={<MyCommunitiesPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("MyCommunitiesPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("listaAsMinhasComunidadesComLigacaoParaAsDefinicoes", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify([
            {
              id: 1,
              slug: "tips-do-ze",
              name: "Tips do Zé",
              priceMonthlyCents: 999,
              currency: "EUR",
              status: "ACTIVE",
              ownerId: 7,
              ownerDisplayName: "Zé",
              createdAt: "2026-01-01T00:00:00Z",
              ownedByViewer: true,
            },
          ]),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByText("Tips do Zé")).toBeInTheDocument();
    });

    const ligacao = screen.getByRole("link", { name: "Definições" });
    expect(ligacao).toHaveAttribute("href", "/comunidades/tips-do-ze/definicoes");
  });
});
