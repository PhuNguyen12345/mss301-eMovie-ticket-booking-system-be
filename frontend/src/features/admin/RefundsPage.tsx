import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { Refund } from "../booking/api";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { Dialog } from "../../components/ui/Dialog";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { formatMoney } from "../../lib/date";
export function RefundsPage() {
  const { mode } = useSession();
  if (mode === "demo")
    return (
      <main className="page">
        <h1>Hoàn tiền</h1>
        <Placeholder
          title="Danh sách yêu cầu hoàn tiền"
          description="Danh sách sẽ hiển thị khi kết nối dịch vụ thanh toán."
        />
      </main>
    );
  return <RefundList />;
}
function RefundList() {
  const { booking } = useServices();
  const { current } = useSession();
  const cache = useQueryClient();
  const [status, setStatus] = useState<Refund["status"]>("PENDING");
  const [selected, setSelected] = useState<Refund | null>(null);
  const key = ["private", current?.user.userId, "refunds"] as const;
  const query = useQuery({
    queryKey: [...key, status],
    queryFn: ({ signal }) => booking.refunds(status, signal),
  });
  const mutation = useMutation({
    mutationFn: (id: number) => booking.completeRefund(id),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: key });
      setSelected(null);
    },
  });
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Hoàn tiền</h1>
          <p>
            Ghi nhận khoản hoàn tiền sau khi nhân viên đã chuyển tiền cho khách.
          </p>
        </div>
        <Field label="Trạng thái hoàn tiền">
          <select
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as Refund["status"])
            }
          >
            <option value="PENDING">Chờ xử lý</option>
            <option value="COMPLETED">Đã hoàn tiền</option>
            <option value="REJECTED">Đã từ chối</option>
          </select>
        </Field>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorMessage
          error={query.error}
          retry={() => {
            void query.refetch();
          }}
        />
      ) : (
        <div className="booking-list">
          {query.data.map((refund) => (
            <article className="booking-card" key={refund.id}>
              <div>
                <h2>Đơn vé #{refund.bookingId}</h2>
                <p className="muted">Giao dịch #{refund.orderCode}</p>
                <p>{refund.reason}</p>
              </div>
              <div>
                <strong>{formatMoney(refund.amount)}</strong>
                {refund.status === "PENDING" && (
                  <Button
                    onClick={() => {
                      mutation.reset();
                      setSelected(refund);
                    }}
                  >
                    Ghi nhận đã hoàn
                  </Button>
                )}
              </div>
            </article>
          ))}
          {!query.data.length && (
            <EmptyState title="Không có yêu cầu hoàn tiền" />
          )}
        </div>
      )}
      {selected && (
        <Dialog
          title="Xác nhận đã hoàn tiền"
          busy={mutation.isPending}
          onClose={() => setSelected(null)}
        >
          <p className="dialog-description">
            Xác nhận đã chuyển {formatMoney(selected.amount)} cho khách của đơn
            #{selected.bookingId}. Thao tác này chỉ ghi nhận kết quả, không thực
            hiện chuyển tiền.
          </p>
          <ErrorMessage error={mutation.error} />
          <div className="dialog-actions">
            <Button
              disabled={mutation.isPending}
              onClick={() => setSelected(null)}
            >
              Quay lại
            </Button>
            <Button
              variant="primary"
              pending={mutation.isPending}
              onClick={() => mutation.mutate(selected.id)}
            >
              Đã chuyển tiền
            </Button>
          </div>
        </Dialog>
      )}
    </main>
  );
}
