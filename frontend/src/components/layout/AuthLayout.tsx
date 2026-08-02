import { Outlet } from "react-router-dom";

import { Brand } from "@/components/layout/Brand";

/**
 * Moldura de `/entrar` e `/registo`: um cartão centrado, sem navegação a
 * distrair. A frase por baixo lembra o que a aplicação faz — é a primeira
 * coisa que muita gente vê do SeerHub.
 */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-12">
      <div className="w-full max-w-[26rem] animate-fade-up">
        <div className="mb-8 flex justify-center">
          <Brand />
        </div>

        <div className="rounded-2xl border border-white/[0.08] bg-ink-900/50 p-7 shadow-lifted backdrop-blur-xl sm:p-8">
          <Outlet />
        </div>

        <p className="mt-8 text-center text-xs text-ink-500">
          Todas as tips dos teus tipsters favoritos, num só sítio.
        </p>
      </div>
    </div>
  );
}
