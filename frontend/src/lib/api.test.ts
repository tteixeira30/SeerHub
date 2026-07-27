import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiError, CORRELATION_ID_HEADER } from "@/lib/api";

describe("apiFetch", () => {
  beforeEach(() => {
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
});
