import { useEffect, useState } from "react";
import { Plus, RotateCcw } from "lucide-react";
import { useServices } from "../../../app/ServicesProvider";
import { Button } from "../../../components/ui/Button";
import { ErrorMessage, Loading } from "../../../components/ui/Feedback";
import { Field } from "../../../components/ui/Field";
import {
  formatDate,
  formatMoney,
  formatTime,
  hasStarted,
} from "../../../lib/date";
import { usePriceDraft } from "../hooks/usePriceDraft";
import {
  useReplaceSeatPrices,
  useSeatPrices,
} from "../hooks/useShowtimeQueries";
import type { Movie, SeatPrice, Showtime } from "../model/contracts";

type Props = {
  show: Showtime;
  movie?: Movie;
  canWrite: boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBusyChange: (busy: boolean) => void;
  notify: (message: string) => void;
  onCancel: () => void;
};
export function PricePanel(props: Props) {
  const query = useSeatPrices(props.show.id);
  return (
    <aside className="price-panel">
      <header className="panel-header">
        <div>
          <h2>Bảng giá vé</h2>
          <p>{props.movie?.title ?? `Phim #${props.show.movieId}`}</p>
        </div>
        <span className="muted">#{props.show.id}</span>
      </header>
      <div className="panel-show-info">
        <span>
          {formatDate(props.show.startTime)} ·{" "}
          {formatTime(props.show.startTime)}
        </span>
        <span>Phòng {props.show.screenId}</span>
      </div>
      {query.isError && query.data && (
        <ErrorMessage
          error={query.error}
          retry={() => {
            void query.refetch();
          }}
        />
      )}
      {query.isPending ? (
        <Loading label="Đang tải bảng giá…" />
      ) : query.isError && !query.data ? (
        <ErrorMessage
          error={query.error}
          retry={() => {
            void query.refetch();
          }}
        />
      ) : (
        <PriceEditor
          {...props}
          prices={query.data!}
          refreshing={query.isFetching || query.isError}
        />
      )}
      <footer className="panel-footer">
        <span>
          {props.show.status === "CANCELLED"
            ? "Suất chiếu đã hủy"
            : "Áp dụng cho suất chiếu này"}
        </span>
        {props.show.status === "SCHEDULED" && (
          <Button
            variant="ghost"
            disabled={!props.canWrite || hasStarted(props.show.startTime)}
            onClick={props.onCancel}
          >
            Hủy suất chiếu
          </Button>
        )}
      </footer>
    </aside>
  );
}
function PriceEditor({
  show,
  prices,
  refreshing,
  canWrite,
  onDirtyChange,
  onBusyChange,
  notify,
}: Props & { prices: SeatPrice[]; refreshing: boolean }) {
  const { showtimes } = useServices();
  const draft = usePriceDraft(prices, showtimes.seatTypes);
  const mutation = useReplaceSeatPrices(show.id);
  const [error, setError] = useState<unknown>(null);
  const [newType, setNewType] = useState("");
  useEffect(() => {
    onDirtyChange(draft.dirty);
  }, [draft.dirty, onDirtyChange]);
  useEffect(() => {
    onBusyChange(mutation.isPending);
  }, [mutation.isPending, onBusyChange]);
  useEffect(
    () => () => {
      onDirtyChange(false);
      onBusyChange(false);
    },
    [onDirtyChange, onBusyChange],
  );
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const submitted = draft.submit();
      const saved = await mutation.mutateAsync(submitted);
      draft.reset(saved);
      notify(
        submitted.length
          ? "Đã cập nhật bảng giá."
          : "Đã áp dụng giá cơ bản cho tất cả loại ghế.",
      );
    } catch (error) {
      setError(error);
    }
  }
  return (
    <form onSubmit={save} className="price-form">
      <div className="base-price">
        <span>Giá cơ bản</span>
        <strong>{formatMoney(show.basePrice)}</strong>
      </div>
      {!canWrite && (
        <p className="notice">Đăng nhập tài khoản quản trị để thay đổi giá.</p>
      )}
      {draft.dirty && draft.remoteChanged && (
        <div className="notice" role="alert">
          Bảng giá đã được cập nhật ở nơi khác.{" "}
          <Button variant="ghost" onClick={() => draft.reset()}>
            Tải lại bảng giá
          </Button>
        </div>
      )}
      <fieldset disabled={!canWrite || mutation.isPending}>
        <div className="price-fields">
          {draft.fields.map((id) => (
            <Field
              key={id}
              label={`Giá ${showtimes.seatTypes.find((type) => type.id === id)?.name ?? `loại ghế #${id}`}`}
            >
              <input
                className="price-input"
                inputMode="decimal"
                placeholder={`${show.basePrice}`}
                value={draft.values[id]}
                onChange={(event) => {
                  draft.change(id, event.target.value);
                  setError(null);
                }}
              />
            </Field>
          ))}
        </div>
        <div className="add-price-type">
          <Field label="Mã loại ghế mới">
            <input
              type="number"
              min="1"
              step="1"
              value={newType}
              placeholder="VD: 4"
              onChange={(e) => setNewType(e.target.value)}
            />
          </Field>
          <Button
            aria-label="Thêm loại ghế"
            onClick={() => {
              try {
                draft.add(Number(newType));
                setNewType("");
                setError(null);
              } catch (error) {
                setError(error);
              }
            }}
          >
            <Plus size={17} />
          </Button>
        </div>
        <p className="field-hint">Đơn vị: VND. Để trống để dùng giá cơ bản.</p>
        <Button variant="ghost" className="reset-prices" onClick={draft.clear}>
          <RotateCcw size={14} />
          Dùng giá cơ bản cho tất cả
        </Button>
      </fieldset>
      <ErrorMessage error={error} />
      <div className="price-actions">
        <Button
          variant="primary"
          type="submit"
          pending={mutation.isPending}
          disabled={
            !canWrite || !draft.dirty || refreshing || draft.remoteChanged
          }
        >
          Lưu bảng giá
        </Button>
        <Button
          disabled={!draft.dirty || mutation.isPending}
          onClick={() => {
            draft.reset();
            setError(null);
          }}
        >
          Bỏ thay đổi
        </Button>
      </div>
    </form>
  );
}
