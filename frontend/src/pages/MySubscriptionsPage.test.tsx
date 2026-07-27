import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { MySubscriptionsPage } from "@/pages/MySubscriptionsPage";

const SUBSCRICOES = [1, 2, 3, 4, 5].map((i) => ({
  communityId: i,
  slug: `comunidade-${i}`,
  communityName: `Comunidade ${i}`,
  priceMonthlyCents: 500 * i,
  currency: "EUR",
  role: "MEMBER",
  status: "ACTIVE",
  joinedAt: "2026-07-27T00:00:00Z",
  expiresAt: "2026-08-26T00:00:00Z",
  active: true,
}));

function renderizar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/subscricoes"]}>
        <MySubscriptionsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("MySubscriptionsPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("listaTodasAsComunidadesSubscritas", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(SUBSCRICOES), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByText("Comunidade 1")).toBeInTheDocument();
    });
    for (const subscricao of SUBSCRICOES) {
      expect(screen.getByText(subscricao.communityName)).toBeInTheDocument();
    }
    expect(screen.getAllByText("ACTIVE")).toHaveLength(SUBSCRICOES.length);
  });
});
