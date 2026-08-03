import { useQuery } from "@tanstack/react-query";

import { Brand } from "@/components/layout/Brand";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { IconChart, IconCommunities, IconSparkles } from "@/components/ui/Icons";
import { Skeleton } from "@/components/ui/Skeleton";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";

interface HealthResponse {
  status: string;
  components?: Record<string, { status: string }>;
}

const DESTAQUES = [
  {
    icon: <IconCommunities className="h-5 w-5" />,
    title: "Comunidades por subscrição",
    description:
      "Cria a tua comunidade, defines o preço mensal e decides quem entra. Os teus subscritores acompanham-te sem sair daqui.",
  },
  {
    icon: <IconSparkles className="h-5 w-5" />,
    title: "Tips organizadas por ti",
    description:
      "Escreves as tuas previsões como sempre escreveste; o SeerHub trata de as apresentar em tips estruturadas e legíveis.",
  },
  {
    icon: <IconChart className="h-5 w-5" />,
    title: "Estatísticas que não mentem",
    description:
      "O histórico de cada tipster fica à vista de todos — quem acerta ganha reputação, não seguidores por acaso.",
  },
];

/**
 * Página inicial pública. Continua a ser a prova de que a cadeia toda
 * responde (frontend → proxy → backend → base de dados), agora num
 * indicador discreto de estado do serviço em vez de ser a página inteira.
 */
export function HealthPage() {
  const { autenticado } = useAuth();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["health"],
    queryFn: () => apiFetch<HealthResponse>("/actuator/health"),
  });

  return (
    <div className="flex min-h-screen flex-col">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-6 sm:px-8">
        <Brand />
        <nav className="flex items-center gap-2" aria-label="Acesso à conta">
          {autenticado ? (
            <ButtonLink to="/comunidades" size="sm">
              Ir para o meu hub
            </ButtonLink>
          ) : (
            <>
              <ButtonLink to="/entrar" variant="ghost" size="sm">
                Entrar
              </ButtonLink>
              <ButtonLink to="/registo" size="sm">
                Criar conta
              </ButtonLink>
            </>
          )}
        </nav>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-5 sm:px-8">
        <section className="animate-fade-up py-16 text-center sm:py-24">
          <Badge tone="brand" className="mb-8">
            <IconSparkles className="h-3.5 w-3.5" />O hub dos tipsters
          </Badge>

          <h1 className="bg-gradient-to-br from-white via-white to-brand-300 bg-clip-text text-[clamp(3rem,12vw,6.5rem)] font-semibold leading-[0.95] tracking-tighter text-transparent">
            SeerHub
          </h1>

          <p className="mx-auto mt-7 max-w-xl text-lg leading-relaxed text-ink-300">
            Todas as tips dos teus tipsters favoritos num só sítio. Sem grupos de Telegram
            perdidos, sem links de pagamento espalhados por cinco plataformas.
          </p>

          <div className="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <ButtonLink to={autenticado ? "/comunidades" : "/registo"} size="lg">
              {autenticado ? "Ir para o meu hub" : "Começar agora"}
            </ButtonLink>
            <ButtonLink to={autenticado ? "/subscricoes" : "/entrar"} variant="secondary" size="lg">
              {autenticado ? "Ver subscrições" : "Já tenho conta"}
            </ButtonLink>
          </div>
        </section>

        <section className="grid gap-4 pb-20 md:grid-cols-3">
          {DESTAQUES.map((destaque) => (
            <Card key={destaque.title} interactive className="p-6">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-400/10 text-brand-300 ring-1 ring-inset ring-brand-400/20">
                {destaque.icon}
              </div>
              <h2 className="mt-5 text-base font-semibold tracking-tight text-ink-50">
                {destaque.title}
              </h2>
              <p className="mt-2 text-sm leading-relaxed text-ink-400">{destaque.description}</p>
            </Card>
          ))}
        </section>
      </main>

      <footer className="border-t border-white/[0.06]">
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-4 px-5 py-6 sm:flex-row sm:px-8">
          <p className="text-xs text-ink-500">
            SeerHub &middot; feito para quem partilha tips e para quem as segue.
          </p>
          <div className="flex items-center gap-2.5 text-xs text-ink-500">
            <span>Estado do serviço</span>
            {isLoading ? (
              <Skeleton className="h-6 w-16 rounded-full" />
            ) : (
              <StatusBadge value={isError || !data ? "DOWN" : data.status} />
            )}
          </div>
        </div>
      </footer>
    </div>
  );
}
