import { Button } from "../../../components/ui/Button";
import { Dialog } from "../../../components/ui/Dialog";
import { ErrorMessage } from "../../../components/ui/Feedback";
import { formatDate, formatTime } from "../../../lib/date";
import { useCancelShowtime } from "../hooks/useShowtimeQueries";
import type { Showtime } from "../model/contracts";
export function CancelShowtimeDialog({
  show,
  title,
  close,
  cancelled,
}: {
  show: Showtime;
  title: string;
  close: () => void;
  cancelled: () => void;
}) {
  const mutation = useCancelShowtime();
  return (
    <Dialog title="Hủy suất chiếu?" onClose={close} busy={mutation.isPending}>
      <p className="dialog-description">
        {title} · {formatDate(show.startTime)} · {formatTime(show.startTime)} ·
        Phòng {show.screenId}.
      </p>
      <p className="muted">
        Suất chiếu sẽ được hủy. Hệ thống xử lý các đơn liên quan và phát voucher
        bù cho đơn đã trả tiền theo chính sách của rạp. Có thể kiểm tra và xử lý
        lại ở mục Suất đã hủy.
      </p>
      <ErrorMessage error={mutation.error} />
      <div className="dialog-actions">
        <Button disabled={mutation.isPending} onClick={close}>
          Giữ lại
        </Button>
        <Button
          variant="danger"
          pending={mutation.isPending}
          onClick={() => mutation.mutate(show.id, { onSuccess: cancelled })}
        >
          Xác nhận hủy
        </Button>
      </div>
    </Dialog>
  );
}
