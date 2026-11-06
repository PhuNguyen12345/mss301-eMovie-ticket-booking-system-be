import { useId, type ReactElement, cloneElement } from "react";

type Control = ReactElement<{
  id?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
}>;
export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: Control;
}) {
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {cloneElement(children, {
        id,
        "aria-describedby": error || hint ? `${id}-help` : undefined,
        "aria-invalid": !!error,
      })}
      {(error || hint) && (
        <span
          id={`${id}-help`}
          className={error ? "field-error" : "field-hint"}
        >
          {error || hint}
        </span>
      )}
    </div>
  );
}
