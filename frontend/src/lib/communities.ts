/**
 * Cliente das comunidades (F02). Espelha `CommunityResponse` do backend e
 * expõe as quatro chamadas sobre `apiFetch`, mais a conversão
 * euros↔cêntimos, isolada aqui para viver num sítio só e ser testável
 * (FE4) — ver risco em aberto 7 do plano de F02.
 */

import { apiFetch } from "@/lib/api";

/** Espelha `CommunityResponse` (`pt.seerhub.community.api.CommunityResponse`). */
export interface Comunidade {
  id: number;
  slug: string;
  name: string;
  description?: string;
  avatarUrl?: string;
  bannerUrl?: string;
  priceMonthlyCents: number;
  currency: string;
  status: "ACTIVE" | "SUSPENDED";
  ownerId: number;
  ownerDisplayName: string;
  createdAt: string;
  ownedByViewer: boolean;
}

export interface DadosComunidade {
  name: string;
  description?: string;
  avatarUrl?: string;
  bannerUrl?: string;
  priceMonthlyCents: number;
}

export function criarComunidade(dados: DadosComunidade): Promise<Comunidade> {
  return apiFetch<Comunidade>("/api/communities", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dados),
  });
}

export function listarMinhasComunidades(): Promise<Comunidade[]> {
  return apiFetch<Comunidade[]>("/api/me/communities");
}

export function obterComunidade(slug: string): Promise<Comunidade> {
  return apiFetch<Comunidade>(`/api/communities/${slug}`);
}

export function guardarComunidade(slug: string, dados: DadosComunidade): Promise<Comunidade> {
  return apiFetch<Comunidade>(`/api/communities/${slug}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dados),
  });
}

/** "9,99" ou "9.99" → 999. Único ponto de conversão euros→cêntimos (FE4). */
export function eurosParaCentimos(valor: string): number {
  const normalizado = valor.replace(",", ".");
  return Math.round(Number(normalizado) * 100);
}

/** 999 → "9.99". */
export function centimosParaEuros(cents: number): string {
  return (cents / 100).toFixed(2);
}
