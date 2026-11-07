import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useSession, useServices } from "../../app/ServicesProvider";
import { useBookingShowtime, useSeatMap, useHoldSeats } from "./hooks";
import { useMovie } from "../movies/hooks";
import { LoginDialog } from "../auth/LoginDialog";
import { SeatGrid } from "./SeatGrid";
import { Button } from "../../components/ui/Button";
import { Placeholder } from "../../components/ui/Placeholder";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import {
  formatDate,
  formatTime,
  formatMoney,
  hasStarted,
} from "../../lib/date";
import { useNavigationGuard } from "../../lib/useNavigationGuard";
export function SeatsPage() {
  const id = Number(useParams().showtimeId);
  const { mode } = useSession();
  if (!Number.isSafeInteger(id) || id <= 0)
    return (
      <main className="page">
        <EmptyState title="Suất chiếu không hợp lệ" />
      </main>
    );
  if (mode === "demo")
    return (
      <main className="page">
        <Link to="/" className="text-link">
          ← Danh sách phim
        </Link>
        <Placeholder
          title="Chọn ghế và đặt vé"
          description="Sơ đồ ghế sẽ hiển thị khi kết nối dịch vụ đặt vé. Bản xem trước không tạo ghế hoặc giao dịch mẫu."
        />
      </main>
    );
  return <SeatSelection key={id} id={id} />;
}
function SeatSelection({ id }: { id: number }) {
  const navigate = useNavigate();
  const { session: sessionStore } = useServices();
  const session = useSession();
  const show = useBookingShowtime(id);
  const map = useSeatMap(id);
  const movie = useMovie(show.data?.movieId ?? 0);
  const hold = useHoldSeats(id);
  const [selected, setSelected] = useState<number[]>([]);
  const [login, setLogin] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [completed, setCompleted] = useState<number | null>(null);
  useEffect(() => {
    if (completed) navigate(`/bookings/${completed}`);
  }, [completed, navigate]);
  useNavigationGuard(
    hold.isPending && !completed,
    "Đang giữ ghế. Rời trang có thể bỏ lỡ kết quả đặt vé.",
  );
  const available =
    map.data?.seats.filter(
      (s) => selected.includes(s.seatId) && s.status === "AVAILABLE",
    ) ?? [];
  const staleSelection = available.length !== selected.length;
  const bookable =
    !show.isError &&
    show.data?.status === "SCHEDULED" &&
    !hasStarted(show.data.startTime);
  async function submit() {
    if (!session.current) {
      setLogin(true);
      return;
    }
    if (!bookable || !available.length || staleSelection || map.isError) return;
    setError(null);
    const token = session.current.token;
    try {
      const booking = await hold.mutateAsync(available.map((s) => s.seatId));
      if (sessionStore.getSnapshot()?.token !== token) return;
      setCompleted(booking.id);
    } catch (error) {
      setError(error);
    }
  }
  return (
    <main className="page cinema-page">
      <Link
        className="text-link"
        to={show.data ? `/movies/${show.data.movieId}` : "/"}
      >
        ← Chọn suất khác
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">01 / CHỌN GHẾ</p>
          <h1>{movie.data?.title || "Chọn ghế"}</h1>
          {show.data && (
            <p>
              {formatDate(show.data.startTime)} ·{" "}
              {formatTime(show.data.startTime)} · Rạp #{show.data.cinemaId} ·
              Phòng #{show.data.screenId}
            </p>
          )}
        </div>
      </div>
      <ErrorMessage
        error={show.error}
        retry={() => {
          void show.refetch();
        }}
      />
      <div className="checkout-grid">
        <section className="surface seat-section">
          {map.isPending ? (
            <Loading label="Đang tải sơ đồ ghế…" />
          ) : map.isError ? (
            <>
              <ErrorMessage
                error={map.error}
                retry={() => {
                  void map.refetch();
                }}
              />
              <p className="muted">
                Chưa thể tải sơ đồ ghế. Bạn có thể thử lại sau hoặc chọn suất
                khác.
              </p>
            </>
          ) : !map.data.seats.length ? (
            <EmptyState title="Phòng chưa có sơ đồ ghế" />
          ) : (
            <SeatGrid
              seats={map.data.seats}
              selected={selected}
              disabled={hold.isPending || !bookable}
              toggle={(seatId) => {
                setError(null);
                setSelected((previous) =>
                  previous.includes(seatId)
                    ? previous.filter((value) => value !== seatId)
                    : [...previous, seatId],
                );
              }}
            />
          )}
          {show.data && !bookable && (
            <p className="notice">
              Suất chiếu đã bắt đầu hoặc đã hủy, không thể đặt vé.
            </p>
          )}
        </section>
        <aside className="surface order-summary">
          <h2>Ghế của bạn</h2>
          <p className="selected-seats">
            {available.map((s) => `${s.rowCode}${s.seatNumber}`).join(", ") ||
              "Chưa chọn ghế"}
          </p>
          {staleSelection && (
            <p className="notice" role="alert">
              Một số ghế vừa được người khác giữ. Bỏ chọn ghế cũ để tiếp tục.
              <Button
                onClick={() => setSelected(available.map((s) => s.seatId))}
              >
                Cập nhật lựa chọn
              </Button>
            </p>
          )}
          <dl className="summary-lines">
            <div>
              <dt>Số ghế</dt>
              <dd>{available.length}</dd>
            </div>
            <div>
              <dt>Tạm tính</dt>
              <dd>
                {formatMoney(
                  available.reduce((sum, seat) => sum + seat.price, 0),
                )}
              </dd>
            </div>
          </dl>
          <p className="muted">
            Ghế chỉ được giữ sau khi bạn tiếp tục. Tổng tiền và thời hạn giữ ghế
            được xác nhận ở bước kế tiếp.
          </p>
          <ErrorMessage error={error} />
          <Button
            variant="primary"
            pending={hold.isPending}
            disabled={
              !bookable ||
              !available.length ||
              staleSelection ||
              map.isError ||
              !!completed
            }
            onClick={() => {
              void submit();
            }}
          >
            {session.current ? "Giữ ghế & tiếp tục" : "Đăng nhập để đặt vé"}
          </Button>
        </aside>
      </div>
      {login && <LoginDialog close={() => setLogin(false)} />}
    </main>
  );
}
