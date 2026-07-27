import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { clearSession } from "@/lib/api";
import { AuthProvider } from "@/lib/auth";
import { RequireAuth } from "@/components/RequireAuth";

function renderizar() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/protegida"]}>
        <Routes>
          <Route
            path="/protegida"
            element={
              <RequireAuth>
                <p>Conteúdo protegido</p>
              </RequireAuth>
            }
          />
          <Route path="/entrar" element={<p>Página de login</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe("RequireAuth", () => {
  beforeEach(() => {
    clearSession();
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("rotaProtegidaSemSessaoRedirecionaParaEntrar", async () => {
    renderizar();

    await waitFor(() => {
      expect(screen.getByText("Página de login")).toBeInTheDocument();
    });
    expect(screen.queryByText("Conteúdo protegido")).not.toBeInTheDocument();
  });
});
