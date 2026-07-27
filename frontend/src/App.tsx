import { Route, Routes } from "react-router-dom";

import { RequireAuth } from "@/components/RequireAuth";
import { AuthProvider } from "@/lib/auth";
import { AccountPage } from "@/pages/AccountPage";
import { CommunityPage } from "@/pages/CommunityPage";
import { CommunitySettingsPage } from "@/pages/CommunitySettingsPage";
import { CreateCommunityPage } from "@/pages/CreateCommunityPage";
import { HealthPage } from "@/pages/HealthPage";
import { LoginPage } from "@/pages/LoginPage";
import { MyCommunitiesPage } from "@/pages/MyCommunitiesPage";
import { MySubscriptionsPage } from "@/pages/MySubscriptionsPage";
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
        <Route
          path="/comunidades"
          element={
            <RequireAuth>
              <MyCommunitiesPage />
            </RequireAuth>
          }
        />
        <Route
          path="/comunidades/nova"
          element={
            <RequireAuth>
              <CreateCommunityPage />
            </RequireAuth>
          }
        />
        <Route
          path="/comunidades/:slug/definicoes"
          element={
            <RequireAuth>
              <CommunitySettingsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/subscricoes"
          element={
            <RequireAuth>
              <MySubscriptionsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/comunidades/:slug"
          element={
            <RequireAuth>
              <CommunityPage />
            </RequireAuth>
          }
        />
      </Routes>
    </AuthProvider>
  );
}
