import { Route, Routes } from "react-router-dom";

import { RequireAuth } from "@/components/RequireAuth";
import { AuthProvider } from "@/lib/auth";
import { AccountPage } from "@/pages/AccountPage";
import { HealthPage } from "@/pages/HealthPage";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";

export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<HealthPage />} />
        <Route path="/registo" element={<RegisterPage />} />
        <Route path="/entrar" element={<LoginPage />} />
        <Route
          path="/conta"
          element={
            <RequireAuth>
              <AccountPage />
            </RequireAuth>
          }
        />
      </Routes>
    </AuthProvider>
  );
}
