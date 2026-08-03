import { Route, Routes } from "react-router-dom";

import { RequireAuth } from "@/components/RequireAuth";
import { AppLayout } from "@/components/layout/AppLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { AuthProvider } from "@/lib/auth";
import { AccountPage } from "@/pages/AccountPage";
import { CommunityModeratorsPage } from "@/pages/CommunityModeratorsPage";
import { CommunityPage } from "@/pages/CommunityPage";
import { CommunitySettingsPage } from "@/pages/CommunitySettingsPage";
import { CreateCommunityPage } from "@/pages/CreateCommunityPage";
import { HealthPage } from "@/pages/HealthPage";
import { LoginPage } from "@/pages/LoginPage";
import { MyCommunitiesPage } from "@/pages/MyCommunitiesPage";
import { MySubscriptionsPage } from "@/pages/MySubscriptionsPage";
import { RegisterPage } from "@/pages/RegisterPage";

/**
 * Três molduras, escolhidas pela rota: a página inicial é a sua própria
 * página, `/entrar` e `/registo` partilham o cartão centrado do
 * {@link AuthLayout}, e tudo o que exige sessão vive dentro do
 * {@link AppLayout} — atrás de um único {@link RequireAuth}, em vez de um
 * por rota.
 */
export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<HealthPage />} />

        <Route element={<AuthLayout />}>
          <Route path="/registo" element={<RegisterPage />} />
          <Route path="/entrar" element={<LoginPage />} />
        </Route>

        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route path="/conta" element={<AccountPage />} />
          <Route path="/comunidades" element={<MyCommunitiesPage />} />
          <Route path="/comunidades/nova" element={<CreateCommunityPage />} />
          <Route path="/comunidades/:slug/definicoes" element={<CommunitySettingsPage />} />
          <Route path="/comunidades/:slug/moderadores" element={<CommunityModeratorsPage />} />
          <Route path="/comunidades/:slug" element={<CommunityPage />} />
          <Route path="/subscricoes" element={<MySubscriptionsPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
