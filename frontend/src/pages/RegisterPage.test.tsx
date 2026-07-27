import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { clearSession, getAccessToken, getRefreshToken } from "@/lib/api";
import { AuthProvider } from "@/lib/auth";
import { RegisterPage } from "@/pages/RegisterPage";

function renderizar() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/registo"]}>
        <Routes>
          <Route path="/registo" element={<RegisterPage />} />
          <Route path="/conta" element={<p>Página da conta</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe("RegisterPage", () => {
  beforeEach(() => {
    clearSession();
    localStorage.clear();

    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            accessToken: "token-de-acesso",
            refreshToken: "token-de-refresh",
            tokenType: "Bearer",
            expiresIn: 900,
            user: {
              id: 1,
              email: "nova@exemplo.pt",
              username: "nova",
              displayName: "Nova",
              globalRole: "USER",
              status: "ACTIVE",
              createdAt: "2026-01-01T00:00:00Z",
            },
          }),
          { status: 201, headers: { "Content-Type": "application/json" } }
        )
      )
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("submeterORegistoGuardaOsTokensERedirecionaParaAConta", async () => {
    renderizar();

    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "nova@exemplo.pt" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-longa-123" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar conta" }));

    await waitFor(() => {
      expect(screen.getByText("Página da conta")).toBeInTheDocument();
    });

    expect(getAccessToken()).toBe("token-de-acesso");
    expect(getRefreshToken()).toBe("token-de-refresh");
  });
});
