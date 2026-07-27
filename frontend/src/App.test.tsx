import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "@/App";

function renderizarApp() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("App", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify({ status: "UP", components: { db: { status: "UP" } } }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renderizaOTituloDaAplicacao", async () => {
    renderizarApp();

    expect(await screen.findByText("SeerHub")).toBeInTheDocument();
  });

  it("mostraOEstadoDoServicoDevolvidoPelaApi", async () => {
    renderizarApp();

    await waitFor(() => {
      expect(screen.getByText(/UP/)).toBeInTheDocument();
    });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/actuator/health"),
      expect.anything()
    );
  });
});
