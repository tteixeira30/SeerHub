import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { CommunityPage } from "@/pages/CommunityPage";

const SLUG = "tips-do-ze";
const COMUNIDADE = {
  id: 1,
  slug: SLUG,
  name: "Tips do Zé",
  description: "A melhor comunidade de tips.",
  avatarUrl: undefined,
  bannerUrl: undefined,
  priceMonthlyCents: 999,
  currency: "EUR",
  status: "ACTIVE",
  ownerId: 99,
  ownerDisplayName: "Zé",
  createdAt: "2026-01-01T00:00:00Z",
  ownedByViewer: false,
};

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
      <MemoryRouter initialEntries={[`/comunidades/${SLUG}`]}>
        <Routes>
          <Route path="/comunidades/:slug" element={<CommunityPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

/**
 * `CommunityPage` faz três pedidos (perfil, `/access`, `/member-area`) —
 * risco em aberto 5 do plano de F03. O mock encaminha por URL e **lança**
 * para qualquer URL não previsto, para um pedido esquecido rebentar o
 * teste em vez de passar despercebido.
 */
function instalarFetchMock(estado: { subscrito: boolean; status: "ACTIVE" | "CANCELLED" | "EXPIRED" | null; expiresAt: string | null }) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const metodo = init?.method ?? "GET";

      if (url.endsWith(`/api/communities/${SLUG}/subscription`) && metodo === "POST") {
        estado.subscrito = true;
        estado.status = "ACTIVE";
        estado.expiresAt = "2026-08-26T12:00:00.000Z";
        return jsonResponse(
            { communityId: 1, slug: SLUG, communityName: COMUNIDADE.name, priceMonthlyCents: 999, currency: "EUR",
              role: "MEMBER", status: "ACTIVE", joinedAt: "2026-07-27T00:00:00Z", expiresAt: estado.expiresAt, active: true },
            201);
      }

      if (url.endsWith(`/api/communities/${SLUG}/subscription`) && metodo === "DELETE") {
        estado.status = "CANCELLED";
        return jsonResponse(
            { communityId: 1, slug: SLUG, communityName: COMUNIDADE.name, priceMonthlyCents: 999, currency: "EUR",
              role: "MEMBER", status: "CANCELLED", joinedAt: "2026-07-27T00:00:00Z", expiresAt: estado.expiresAt, active: true },
            200);
      }

      if (url.endsWith("/access")) {
        if (!estado.subscrito) {
          return jsonResponse({
            communityId: 1, slug: SLUG, premium: false, manager: false, role: null,
            status: estado.status, joinedAt: null, expiresAt: estado.expiresAt,
            priceMonthlyCents: 999, currency: "EUR", permissions: [],
          });
        }
        return jsonResponse({
          communityId: 1, slug: SLUG, premium: estado.status !== "EXPIRED", manager: false, role: "MEMBER",
          status: estado.status, joinedAt: "2026-07-27T00:00:00Z", expiresAt: estado.expiresAt,
          priceMonthlyCents: 999, currency: "EUR",
          permissions: estado.status !== "EXPIRED" ? ["READ_TIPS", "READ_CHAT", "WRITE_CHAT"] : [],
        });
      }

      if (url.endsWith("/member-area")) {
        const temAcesso = estado.subscrito && estado.status !== "EXPIRED";
        if (!temAcesso) {
          return jsonResponse({ detail: "Precisa de uma subscrição ativa para aceder ao conteúdo desta comunidade." }, 403);
        }
        return jsonResponse({
          communityId: 1, slug: SLUG, name: COMUNIDADE.name, role: "MEMBER",
          status: estado.status, joinedAt: "2026-07-27T00:00:00Z", expiresAt: estado.expiresAt,
        });
      }

      if (url.endsWith(`/api/communities/${SLUG}`)) {
        return jsonResponse(COMUNIDADE);
      }

      throw new Error(`URL não esperado no mock de fetch: ${metodo} ${url}`);
    })
  );
}

describe("CommunityPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("subscreverMostraOEstadoAtivoEADataDeFim", async () => {
    const estado: { subscrito: boolean; status: "ACTIVE" | "CANCELLED" | "EXPIRED" | null; expiresAt: string | null } =
        { subscrito: false, status: null, expiresAt: null };
    instalarFetchMock(estado);

    renderizar();

    const botaoSubscrever = await screen.findByRole("button", { name: "Subscrever" });
    fireEvent.click(botaoSubscrever);

    await waitFor(() => {
      expect(screen.getByTestId("estado-subscricao")).toHaveTextContent("2026-08-26T12:00:00.000Z");
    });
    expect(screen.getByRole("button", { name: "Cancelar subscrição" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Bem-vindo à área de membro.")).toBeInTheDocument();
    });
  });

  it("cancelarMantemOAcessoEMostraAteQuandoEValido", async () => {
    const estado = { subscrito: true, status: "ACTIVE" as const, expiresAt: "2026-08-20T12:00:00.000Z" };
    instalarFetchMock(estado);

    renderizar();

    const botaoCancelar = await screen.findByRole("button", { name: "Cancelar subscrição" });
    fireEvent.click(botaoCancelar);

    await waitFor(() => {
      expect(screen.getByTestId("estado-subscricao")).toHaveTextContent("Mantém acesso até 2026-08-20T12:00:00.000Z");
    });
    // A prova de acesso: a área de membro continua acessível depois de cancelar.
    await waitFor(() => {
      expect(screen.getByText("Bem-vindo à área de membro.")).toBeInTheDocument();
    });
  });

  it("erro403NaAreaDeMembroMostraOEcraDeResubscricao", async () => {
    const estado = { subscrito: true, status: "EXPIRED" as const, expiresAt: "2026-07-01T12:00:00.000Z" };
    instalarFetchMock(estado);

    renderizar();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("A sua subscrição expirou.");
    });
    expect(screen.getByRole("button", { name: "Subscrever de novo" })).toBeInTheDocument();
  });

  it("botaoDoEcraDeResubscricaoVoltaASubscrever", async () => {
    const estado: { subscrito: boolean; status: "ACTIVE" | "CANCELLED" | "EXPIRED" | null; expiresAt: string | null } =
        { subscrito: true, status: "EXPIRED", expiresAt: "2026-07-01T12:00:00.000Z" };
    instalarFetchMock(estado);

    renderizar();

    const botaoResubscrever = await screen.findByRole("button", { name: "Subscrever de novo" });
    fireEvent.click(botaoResubscrever);

    await waitFor(() => {
      expect(screen.getByText("Bem-vindo à área de membro.")).toBeInTheDocument();
    });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  /**
   * FE4 (F04): o link "Gerir moderadores" só aparece quando o servidor traz
   * `MANAGE_MODERATORS` em `/access` — nunca uma decisão do cliente.
   */
  it("linkDeGerirModeradoresSoApareceComAPermissaoDoServidor", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);

        if (url.endsWith("/access")) {
          return jsonResponse({
            communityId: 1, slug: SLUG, premium: true, manager: true, role: "OWNER",
            status: "ACTIVE", joinedAt: "2026-01-01T00:00:00Z", expiresAt: null,
            priceMonthlyCents: 999, currency: "EUR",
            permissions: ["MANAGE_MODERATORS", "EDIT_COMMUNITY", "DELETE_COMMUNITY"],
          });
        }

        if (url.endsWith("/member-area")) {
          return jsonResponse({
            communityId: 1, slug: SLUG, name: COMUNIDADE.name, role: "OWNER",
            status: "ACTIVE", joinedAt: "2026-01-01T00:00:00Z", expiresAt: null,
          });
        }

        if (url.endsWith(`/api/communities/${SLUG}`)) {
          return jsonResponse(COMUNIDADE);
        }

        throw new Error(`URL não esperado no mock de fetch: ${url}`);
      })
    );

    renderizar();

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Gerir moderadores" })).toBeInTheDocument();
    });
  });
});
