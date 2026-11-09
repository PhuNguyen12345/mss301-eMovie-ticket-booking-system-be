import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { Dialog } from "../../components/ui/Dialog";
import { ErrorMessage } from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { useCancellation } from "./hooks";

export function CancellationsPage() {
  const { mode } = useSession();
  return mode === "demo" ? (
    <main className="page">
      <Placeholder
        title="Xử lý suất chiếu đã hủy"
        description="Mở chế độ API để xử lý đặt vé."
      />
    </main>
  ) : (
    <Cancellation />
  );
}
function Cancellation() {
  const [params] = useSearchParams();
  const [id, setId] = useState(params.get("showtimeId") ?? "");
  const [confirm, setConfirm] = useState<number>();
  const mutation = useCancellation();
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Xử lý suất chiếu đã hủy</h1>
          <p>
            Hệ thống tự xử lý khi hủy suất. Dùng thao tác này để xử lý lại các
            đơn còn lỗi; khoản bù không được phát trùng.
          </p>
        </div>
      </div>
      <Link className="text-link" to="/admin/showtimes">
        Về lịch chiếu →
      </Link>
      <form
        className="surface order-filters"
        onSubmit={(e) => {
          e.preventDefault();
          mutation.reset();
          setConfirm(Number(id));
        }}
      >
        <Field label="ID suất chiếu đã hủy">
          <input
            value={id}
            onChange={(e) => {
              setId(e.target.value);
              mutation.reset();
            }}
            type="number"
            min={1}
            max={Number.MAX_SAFE_INTEGER}
            step={1}
            required
          />
        </Field>
        <Button type="submit" variant="primary">
          Xử lý lại đơn vé
        </Button>
      </form>
      {mutation.data && (
        <section className="surface check-in-result" role="status">
          <h2>Kết quả suất #{mutation.data.showtimeId}</h2>
          <p>{mutation.data.cancelledPending} đơn chờ thanh toán đã hủy</p>
          <p>{mutation.data.cancelledPaid} đơn đã trả tiền được xử lý bù</p>
          <p className={mutation.data.failed ? "notice" : "muted"}>
            {mutation.data.failed} đơn xử lý lỗi
            {mutation.data.failed
              ? ". Có thể chạy lại để xử lý các đơn này."
              : "."}
          </p>
          <Link to="/admin/vouchers">Xem voucher đã phát →</Link>
        </section>
      )}
      {confirm && (
        <Dialog
          title="Xử lý lại đặt vé của suất đã hủy?"
          onClose={() => setConfirm(undefined)}
          busy={mutation.isPending}
        >
          <p className="dialog-description">
            Suất #{confirm}: đơn chờ thanh toán được hủy; đơn đã trả tiền được
            xử lý voucher bù theo chính sách của rạp. Thao tác này không chuyển
            khoản cho khách.
          </p>
          <ErrorMessage error={mutation.error} />
          <div className="dialog-actions">
            <Button
              disabled={mutation.isPending}
              onClick={() => setConfirm(undefined)}
            >
              Quay lại
            </Button>
            <Button
              variant="primary"
              pending={mutation.isPending}
              onClick={async () => {
                try {
                  await mutation.mutateAsync(confirm);
                  setConfirm(undefined);
                } catch {
                  /* Keep explicit retry with server feedback. */
                }
              }}
            >
              Xác nhận xử lý
            </Button>
          </div>
        </Dialog>
      )}
    </main>
  );
}
