import { Clock3 } from "lucide-react";
export function Placeholder({
  title,
  description = "Tính năng đang được hoàn thiện. Vui lòng quay lại sau.",
  compact = false,
}: {
  title: string;
  description?: string;
  compact?: boolean;
}) {
  return (
    <section
      className={`placeholder ${compact ? "compact" : ""}`}
      aria-label={title}
    >
      <Clock3 size={20} aria-hidden="true" />
      <div>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
      <span className="badge badge-demo">Sắp có</span>
    </section>
  );
}
