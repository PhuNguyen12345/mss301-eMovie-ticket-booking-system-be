import { useState } from "react";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Field } from "../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
} from "../../components/ui/Feedback";
import { formatMoney } from "../../lib/date";
import { useCombos } from "./hooks";
import type { Booking } from "../booking/api";

export function BookingCombos({
  booking,
  disabled,
  busy,
  save,
}: {
  booking: Booking;
  disabled: boolean;
  busy: boolean;
  save: (lines: { comboId: number; quantity: number }[]) => Promise<unknown>;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button disabled={disabled} onClick={() => setOpen(true)}>
        Chọn combo
      </Button>
      {open && (
        <ComboPicker
          booking={booking}
          busy={busy}
          save={save}
          close={() => setOpen(false)}
        />
      )}
    </>
  );
}
function ComboPicker({
  booking,
  busy,
  save,
  close,
}: {
  booking: Booking;
  busy: boolean;
  save: (lines: { comboId: number; quantity: number }[]) => Promise<unknown>;
  close: () => void;
}) {
  const query = useCombos();
  const [quantities, setQuantities] = useState<Record<number, number>>(() =>
    Object.fromEntries(
      (booking.combos ?? []).map((c) => [c.comboId, c.quantity]),
    ),
  );
  const [error, setError] = useState<unknown>(null);
  const unavailable = (booking.combos ?? []).filter(
    (c) => query.data && !query.data.some((item) => item.id === c.comboId),
  );
  return (
    <Dialog title="Chọn combo" onClose={close} busy={busy}>
      <p className="muted">
        Tổng tiền được cập nhật sau khi lưu. Không thể đổi combo khi đã tạo liên
        kết thanh toán.
      </p>
      <ErrorMessage
        error={query.error}
        retry={() => {
          void query.refetch();
        }}
      />
      {query.isPending ? (
        <Loading />
      ) : (
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setError(null);
            try {
              await save(
                Object.entries(quantities)
                  .filter(([, quantity]) => quantity > 0)
                  .map(([id, quantity]) => ({ comboId: Number(id), quantity })),
              );
              close();
            } catch (error) {
              setError(error);
            }
          }}
        >
          <fieldset disabled={busy}>
            {query.data?.map((combo) => (
              <Field
                key={combo.id}
                label={`${combo.name} · ${formatMoney(combo.price)}`}
              >
                <input
                  type="number"
                  min={0}
                  max={20}
                  step={1}
                  required
                  value={quantities[combo.id] ?? 0}
                  onChange={(e) =>
                    setQuantities((previous) => ({
                      ...previous,
                      [combo.id]: Number(e.target.value),
                    }))
                  }
                />
              </Field>
            ))}
            {unavailable.map((combo) => (
              <div className="notice" key={combo.comboId}>
                {combo.name} hiện ngừng bán.{" "}
                <Button
                  onClick={() =>
                    setQuantities((previous) => ({
                      ...previous,
                      [combo.comboId]: 0,
                    }))
                  }
                  disabled={!quantities[combo.comboId]}
                >
                  Bỏ khỏi đơn
                </Button>
              </div>
            ))}
            {query.data?.length === 0 && (
              <EmptyState title="Chưa có combo đang bán" />
            )}
          </fieldset>
          <ErrorMessage error={error} />
          <div className="dialog-actions">
            <Button disabled={busy} onClick={close}>
              Đóng
            </Button>
            <Button
              variant="primary"
              type="submit"
              pending={busy}
              disabled={query.isError || !query.data}
            >
              Lưu combo vào vé
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
