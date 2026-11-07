import { useState } from "react";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Field } from "../../components/ui/Field";
import { ErrorMessage } from "../../components/ui/Feedback";
import { useMovieStatus } from "../movies/hooks";
import type { Movie } from "../movies/api";

export function MovieStatusDialog({
  movie,
  close,
}: {
  movie: Movie;
  close: () => void;
}) {
  const [status, setStatus] = useState(movie.status);
  const mutation = useMovieStatus(movie.id);
  return (
    <Dialog
      title={`Đổi trạng thái: ${movie.title}`}
      onClose={close}
      busy={mutation.isPending}
    >
      <form
        onSubmit={async (event) => {
          event.preventDefault();
          if (status === movie.status) return;
          try {
            await mutation.mutateAsync(status);
            close();
          } catch {
            // Keep the selected status visible for an explicit retry.
          }
        }}
      >
        <Field label="Trạng thái mới">
          <select
            value={status}
            disabled={mutation.isPending}
            onChange={(event) => {
              setStatus(event.target.value as Movie["status"]);
              mutation.reset();
            }}
          >
            <option value="WILL_PRESENT">Sắp chiếu</option>
            <option value="PRESENTING">Đang chiếu</option>
            <option value="EXPIRED">Ngừng chiếu</option>
          </select>
        </Field>
        {status === "EXPIRED" && (
          <p className="notice">
            Ngừng chiếu phim không tự hủy lịch chiếu hoặc hoàn tiền các vé đã
            đặt.
          </p>
        )}
        <ErrorMessage error={mutation.error} />
        <div className="dialog-actions">
          <Button onClick={close} disabled={mutation.isPending}>
            Đóng
          </Button>
          <Button
            type="submit"
            variant={status === "EXPIRED" ? "danger" : "primary"}
            pending={mutation.isPending}
            disabled={status === movie.status}
          >
            Xác nhận đổi trạng thái
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
