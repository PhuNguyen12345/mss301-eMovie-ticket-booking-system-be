import { Check, LoaderCircle, X } from "lucide-react";
import { Button } from "./Button";
import { errorMessage } from "../../lib/http";

export function ErrorMessage({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  if (!error) return null;
  return (
    <div className="error-message" role="alert">
      <span>{errorMessage(error)}</span>
      {retry && <Button onClick={retry}>Thử lại</Button>}
    </div>
  );
}
export function Loading({ label = "Đang tải dữ liệu…" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spinner" size={18} />
      {label}
    </div>
  );
}
export function EmptyState({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      {description && <p>{description}</p>}
    </div>
  );
}
export function Toast({
  message,
  dismiss,
}: {
  message: string;
  dismiss: () => void;
}) {
  return message ? (
    <div className="toast" role="status">
      <Check size={18} />
      <span>{message}</span>
      <Button variant="ghost" aria-label="Đóng thông báo" onClick={dismiss}>
        <X size={16} />
      </Button>
    </div>
  ) : null;
}
