import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { clearSession } from "@/lib/api";
import { AuthProvider } from "@/lib/auth";
import { LoginPage } from "@/pages/LoginPage";

function renderizar() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/entrar"]}>
        <Routes>
          <Route path="/entrar" element={<LoginPage />} />
          <Route path="/conta" element={<p>Página da conta</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    clearSession();
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("credenciaisErradasMostramAMensagemDevolvidaPeloBackend", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify({ detail: "Email ou password incorretos." }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      )
    );

    renderizar();

    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "ana@exemplo.pt" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password-errada" } });
    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Email ou password incorretos.");
    expect(screen.queryByText("Página da conta")).not.toBeInTheDocument();
  });
});
