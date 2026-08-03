import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from "react";

import { cn } from "@/lib/cn";

const CONTROL =
  "w-full rounded-xl bg-ink-900/70 px-3.5 py-2.5 text-[0.95rem] text-ink-50 " +
  "ring-1 ring-inset ring-white/10 transition placeholder:text-ink-500 " +
  "hover:ring-white/[0.18] focus:bg-ink-900 focus:outline-none focus:ring-2 focus:ring-brand-400 " +
  "disabled:opacity-50";

/**
 * Etiqueta + controlo. A dica vive **fora** do `<label>` (ligada por
 * `aria-describedby`): assim o texto da etiqueta continua a ser exatamente
 * o que o utilizador — e `getByLabelText` — vê.
 */
function Wrapper({
  id,
  label,
  hint,
  children,
  className,
}: {
  id: string;
  label: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <label htmlFor={id} className="block text-sm font-medium text-ink-200">
        {label}
      </label>
      {children}
      {hint && (
        <p id={`${id}-hint`} className="text-xs text-ink-400">
          {hint}
        </p>
      )}
    </div>
  );
}

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "id" | "className"> {
  id: string;
  label: string;
  hint?: string;
  /** Símbolo fixo à esquerda do valor (por exemplo `€`). Puramente decorativo. */
  prefix?: string;
  className?: string;
}

export function Field({ id, label, hint, prefix, className, ...props }: FieldProps) {
  return (
    <Wrapper id={id} label={label} hint={hint} className={className}>
      <div className="relative">
        {prefix && (
          <span
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm font-medium text-ink-400"
            aria-hidden="true"
          >
            {prefix}
          </span>
        )}
        <input
          id={id}
          aria-describedby={hint ? `${id}-hint` : undefined}
          className={cn(CONTROL, prefix && "pl-8 tabular")}
          {...props}
        />
      </div>
    </Wrapper>
  );
}

interface TextAreaFieldProps
  extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "id" | "className"> {
  id: string;
  label: string;
  hint?: string;
  className?: string;
}

export function TextAreaField({ id, label, hint, className, ...props }: TextAreaFieldProps) {
  return (
    <Wrapper id={id} label={label} hint={hint} className={className}>
      <textarea
        id={id}
        aria-describedby={hint ? `${id}-hint` : undefined}
        className={cn(CONTROL, "min-h-[7rem] resize-y leading-relaxed")}
        {...props}
      />
    </Wrapper>
  );
}
