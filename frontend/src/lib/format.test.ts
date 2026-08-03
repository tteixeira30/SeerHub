import { describe, expect, it } from "vitest";

import { formatarData, iniciais, traduzirEstado } from "@/lib/format";

describe("formatarData", () => {
  it("converteIsoParaDiaMesAno", () => {
    expect(formatarData("2026-08-26T12:00:00.000Z")).toBe("26/08/2026");
  });

  /**
   * A leitura é feita pelo prefixo da string, nunca por `Date`: um instante
   * perto da meia-noite não pode saltar de dia por causa do fuso do browser.
   */
  it("naoMudaODiaConformeOFusoHorario", () => {
    expect(formatarData("2026-01-01T23:59:59.000Z")).toBe("01/01/2026");
    expect(formatarData("2026-01-01T00:00:00.000+05:00")).toBe("01/01/2026");
  });

  it("devolveVazioQuandoNaoHaData", () => {
    expect(formatarData(null)).toBe("");
    expect(formatarData(undefined)).toBe("");
  });

  it("devolveOOriginalQuandoNaoReconheceOFormato", () => {
    expect(formatarData("brevemente")).toBe("brevemente");
  });
});

describe("traduzirEstado", () => {
  it("traduzOsEstadosDeSubscricaoEDeComunidade", () => {
    expect(traduzirEstado("ACTIVE")).toBe("Ativa");
    expect(traduzirEstado("CANCELLED")).toBe("Cancelada");
    expect(traduzirEstado("EXPIRED")).toBe("Expirada");
    expect(traduzirEstado("SUSPENDED")).toBe("Suspensa");
  });

  it("traduzOsPapeisEOEstadoDoServico", () => {
    expect(traduzirEstado("OWNER")).toBe("Dono");
    expect(traduzirEstado("MODERATOR")).toBe("Moderador");
    expect(traduzirEstado("MEMBER")).toBe("Membro");
    expect(traduzirEstado("ADMIN")).toBe("Administrador");
    expect(traduzirEstado("UP")).toBe("Operacional");
    expect(traduzirEstado("DOWN")).toBe("Indisponível");
  });

  /** Um enumerado novo no backend fica visível em vez de desaparecer do ecrã. */
  it("devolveOValorCruQuandoNaoConheceOEstado", () => {
    expect(traduzirEstado("PENDING_REVIEW")).toBe("PENDING_REVIEW");
  });
});

describe("iniciais", () => {
  it("usaAPrimeiraEAUltimaPalavra", () => {
    expect(iniciais("Tips do Zé")).toBe("TZ");
  });

  it("usaDuasLetrasQuandoHaUmaSoPalavra", () => {
    expect(iniciais("Benfica")).toBe("BE");
  });

  it("naoRebentaComNomeVazio", () => {
    expect(iniciais("   ")).toBe("?");
  });
});
