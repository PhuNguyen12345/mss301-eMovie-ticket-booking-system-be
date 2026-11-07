import { Button } from "./Button";
export function Pagination({
  page,
  totalPages,
  change,
  busy = false,
}: {
  page: number;
  totalPages: number;
  change: (page: number) => void;
  busy?: boolean;
}) {
  if (totalPages < 2 && page === 0) return null;
  return (
    <nav className="pagination" aria-label="Phân trang">
      <Button disabled={page === 0 || busy} onClick={() => change(page - 1)}>
        Trang trước
      </Button>
      <span>
        {page + 1} / {Math.max(totalPages, 1)}
      </span>
      <Button
        disabled={page + 1 >= totalPages || busy}
        onClick={() => change(page + 1)}
      >
        Trang sau
      </Button>
    </nav>
  );
}
