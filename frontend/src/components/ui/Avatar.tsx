import { cn } from "@/lib/cn";
import { iniciais } from "@/lib/format";

const SIZES = {
  sm: "h-9 w-9 rounded-lg text-xs",
  md: "h-11 w-11 rounded-xl text-sm",
  lg: "h-16 w-16 rounded-2xl text-lg",
  xl: "h-20 w-20 rounded-2xl text-2xl sm:h-24 sm:w-24",
};

/**
 * Imagem do avatar quando existe; caso contrário as iniciais do nome sobre
 * um degradê da marca. Decorativo (`alt=""`): o nome está sempre ao lado.
 */
export function Avatar({
  name,
  url,
  size = "md",
  className,
}: {
  name: string;
  url?: string | null;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  const base = cn(
    "flex shrink-0 items-center justify-center overflow-hidden",
    "ring-1 ring-inset ring-white/10",
    SIZES[size],
    className
  );

  if (url) {
    return <img src={url} alt="" className={cn(base, "object-cover")} />;
  }

  return (
    <span
      className={cn(
        base,
        "bg-gradient-to-br from-brand-400/25 to-sky-400/15 font-semibold text-brand-200"
      )}
      aria-hidden="true"
    >
      {iniciais(name)}
    </span>
  );
}
