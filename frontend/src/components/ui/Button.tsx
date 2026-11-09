import { LoaderCircle } from "lucide-react";
import type { ButtonHTMLAttributes } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  pending?: boolean;
};
export function Button({
  variant = "secondary",
  pending = false,
  disabled,
  children,
  className = "",
  type = "button",
  ...props
}: Props) {
  return (
    <button
      {...props}
      type={type}
      disabled={disabled || pending}
      aria-busy={pending || undefined}
      className={`button button-${variant} ${className}`}
    >
      {pending && (
        <LoaderCircle className="spinner" size={16} aria-hidden="true" />
      )}
      {children}
    </button>
  );
}
