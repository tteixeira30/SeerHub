import { cn } from "@/lib/cn";

/** Bloco cinzento com brilho a passar, do tamanho do conteúdo que vai substituir. */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-lg bg-white/[0.05]",
        "after:absolute after:inset-0 after:-translate-x-full after:animate-shimmer",
        "after:bg-gradient-to-r after:from-transparent after:via-white/[0.07] after:to-transparent",
        className
      )}
    />
  );
}

/** Grelha de cartões em carregamento, com o mesmo ritmo visual da lista real. */
export function SkeletonCards({ count = 3 }: { count?: number }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: count }, (_, indice) => (
        <div
          key={indice}
          className="rounded-2xl border border-white/[0.07] bg-white/[0.02] p-5"
        >
          <div className="flex items-center gap-3">
            <Skeleton className="h-11 w-11 rounded-xl" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-3.5 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          </div>
          <Skeleton className="mt-6 h-3 w-full" />
          <Skeleton className="mt-2.5 h-3 w-4/5" />
        </div>
      ))}
    </div>
  );
}
