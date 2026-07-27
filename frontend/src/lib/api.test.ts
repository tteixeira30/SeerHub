import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  apiFetch,
  ApiError,
  CORRELATION_ID_HEADER,
  clearSession,
  getAccessToken,
  setSession,
} from "@/lib/api";

describe("apiFetch", () => {
  beforeEach(() => {
    // Estado de sessão (variável de módulo + localStorage) não pode vazar
    // entre testes — sem isto, um teste que chame setSession() contaminaria
    // os seguintes.
    clearSession();
    localStorage.clear();

    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      )
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("enviaCabecalhoXCorrelationIdEmCadaPedido", async () => {
    await apiFetch("/qualquer");

    expect(fetch).toHaveBeenCalledTimes(1);
    const [, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = init.headers as Record<string, string>;

    expect(headers[CORRELATION_ID_HEADER]).toBeDefined();
    expect(headers[CORRELATION_ID_HEADER]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });

  it("lancaApiErrorComDetailECorrelationIdQuandoARespostaNaoEOk", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({ detail: "Pedido inválido.", correlationId: "abc-123" }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        )
      )
    );

    await expect(apiFetch("/qualquer")).rejects.toMatchObject(
      new ApiError(400, "Pedido inválido.", "abc-123")
    );
  });

  it("enviaCabecalhoAuthorizationBearerQuandoHaAccessToken", async () => {
    setSession("meu-token-de-acesso", "refresh-abc");

    await apiFetch("/qualquer");

    const [, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer meu-token-de-acesso");
  });

  it("renovaOTokenUmaVezEmRespostaA401ERepeteOPedidoOriginal", async () => {
    setSession("token-expirado", "refresh-valido");

    let numeroDaChamada = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        numeroDaChamada += 1;
        const url = String(input);

        if (url.includes("/api/auth/refresh")) {
          return new Response(
            JSON.stringify({
              accessToken: "token-novo",
              refreshToken: "refresh-novo",
              tokenType: "Bearer",
              expiresIn: 900,
              user: { id: 1 },
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }

        if (numeroDaChamada === 1) {
          return new Response(JSON.stringify({ detail: "Autenticação necessária." }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
          });
        }

        return new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      })
    );

    const resultado = await apiFetch("/api/recurso-protegido");

    expect(resultado).toEqual({ ok: true });
    // 1: pedido original (401) — 2: renovação — 3: pedido original repetido.
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(getAccessToken()).toBe("token-novo");
  });

  it("quandoARenovacaoFalhaLimpaOsTokensEPropagaOErroSemRepetirOutraVez", async () => {
    setSession("token-expirado", "refresh-invalido");

    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);

        if (url.includes("/api/auth/refresh")) {
          return new Response(JSON.stringify({ detail: "Sessão inválida ou expirada." }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
          });
        }

        return new Response(JSON.stringify({ detail: "Autenticação necessária." }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      })
    );

    await expect(apiFetch("/api/recurso-protegido")).rejects.toMatchObject({
      status: 401,
      detail: "Autenticação necessária.",
    });

    expect(getAccessToken()).toBeNull();
    // Só duas chamadas: o pedido original e a tentativa de renovação —
    // nunca uma segunda repetição do pedido original.
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});
