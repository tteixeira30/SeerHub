import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { CommunitySettingsPage } from "@/pages/CommunitySettingsPage";

const COMUNIDADE = {
  id: 1,
  slug: "tips-do-ze",
  name: "Tips do Zé",
  description: "A melhor comunidade de tips.",
  avatarUrl: "https://exemplo.pt/avatar.png",
  bannerUrl: "https://exemplo.pt/banner.png",
  priceMonthlyCents: 999,
  currency: "EUR",
  status: "ACTIVE",
  ownerId: 1,
  ownerDisplayName: "Zé",
  createdAt: "2026-01-01T00:00:00Z",
  ownedByViewer: true,
};

function renderizar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/comunidades/tips-do-ze/definicoes"]}>
        <Routes>
          <Route path="/comunidades/:slug/definicoes" element={<CommunitySettingsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CommunitySettingsPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("formularioDeDefinicoesVemPreenchidoComOsDadosDaComunidade", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(COMUNIDADE), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByLabelText("Nome")).toHaveValue("Tips do Zé");
    });
    expect(screen.getByLabelText("Descrição")).toHaveValue("A melhor comunidade de tips.");
    expect(screen.getByLabelText("URL do avatar")).toHaveValue("https://exemplo.pt/avatar.png");
    expect(screen.getByLabelText("URL do banner")).toHaveValue("https://exemplo.pt/banner.png");
    expect(screen.getByLabelText("Preço mensal (€)")).toHaveValue("9.99");
  });

  it("guardarEnviaOPrecoConvertidoParaCentimosInteiros", async () => {
    let corpoRecebido: string | undefined;

    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url, init) => {
        const metodo = init?.method ?? "GET";
        if (metodo === "GET") {
          return new Response(JSON.stringify(COMUNIDADE), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          });
        }
        corpoRecebido = init?.body as string;
        return new Response(JSON.stringify({ ...COMUNIDADE, priceMonthlyCents: 999 }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      })
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByLabelText("Nome")).toHaveValue("Tips do Zé");
    });

    fireEvent.change(screen.getByLabelText("Preço mensal (€)"), { target: { value: "9,99" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar" }));

    await waitFor(() => {
      expect(corpoRecebido).toBeDefined();
    });

    const corpo = JSON.parse(corpoRecebido!);
    expect(corpo.priceMonthlyCents).toBe(999);
  });
});
