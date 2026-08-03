import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router-dom";

import { cn } from "@/lib/cn";
import { Spinner } from "@/components/ui/Spinner";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

const BASE =
  "inline-flex select-none items-center justify-center gap-2 rounded-xl font-medium " +
  "transition duration-200 focus-visible:outline-none " +
  "disabled:pointer-events-none disabled:opacity-45";

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    "bg-brand-500 font-semibold text-ink-950 shadow-brand hover:bg-brand-400 " +
    "hover:shadow-[0_14px_36px_-14px_rgba(16,185,129,0.9)] active:translate-y-px",
  secondary:
    "bg-white/[0.06] text-ink-100 ring-1 ring-inset ring-white/10 " +
    "hover:bg-white/[0.1] hover:text-white hover:ring-white/20 active:translate-y-px",
  ghost: "text-ink-300 hover:bg-white/5 hover:text-ink-50",
  danger:
    "bg-rose-500/10 text-rose-200 ring-1 ring-inset ring-rose-500/30 " +
    "hover:bg-rose-500/20 hover:text-rose-100 active:translate-y-px",
};

const SIZES: Record<ButtonSize, string> = {
  sm: "h-9 px-3.5 text-sm",
  md: "h-11 px-5 text-sm",
  lg: "h-12 px-6 text-[0.95rem]",
};

export function buttonClasses(
  variant: ButtonVariant = "primary",
  size: ButtonSize = "md",
  extra?: string
): string {
  return cn(BASE, VARIANTS[variant], SIZES[size], extra);
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Mostra um indicador e desativa o botão, sem mexer no texto (nome acessível estável). */
  loading?: boolean;
  fullWidth?: boolean;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  className,
  disabled,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={buttonClasses(variant, size, cn(fullWidth && "w-full", className))}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  );
}

interface ButtonLinkProps {
  to: string;
  variant?: ButtonVariant;
  size?: ButtonSize;
  className?: string;
  children: ReactNode;
}

/** A mesma pele do {@link Button} para uma ligação de navegação. */
export function ButtonLink({ to, variant = "primary", size = "md", className, children }: ButtonLinkProps) {
  return (
    <Link to={to} className={buttonClasses(variant, size, className)}>
      {children}
    </Link>
  );
}
