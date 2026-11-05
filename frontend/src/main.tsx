import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowRight,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Film,
  LayoutGrid,
  MapPin,
  Plus,
  Search,
  SlidersHorizontal,
  Ticket,
  Users,
  X,
} from "lucide-react";
import "@fontsource/be-vietnam-pro/400.css";
import "@fontsource/be-vietnam-pro/500.css";
import "@fontsource/be-vietnam-pro/600.css";
import "@fontsource/dm-sans/500.css";
import "@fontsource/dm-sans/700.css";
import "@fontsource/dm-serif-display/400.css";
import "./style.css";
import {
  addDays,
  cinemas,
  createShow,
  loadShows,
  money,
  movies,
  persist,
  seatTypes,
  time,
  today,
  validatePrices,
  type Showtime,
} from "./demo";

function Poster({
  movieId,
  large = false,
}: {
  movieId: number;
  large?: boolean;
}) {
  const movie = movies.find((m) => m.id === movieId)!;
  return (
    <div
      aria-hidden="true"
      className={`poster ${movie.art} ${large ? "large" : ""}`}
    >
      <span className="poster-orbit" />
      <span className="poster-land" />
      <span className="poster-name">{movie.title}</span>
      <small>{movie.year} / CINEMA COLLECTION</small>
    </div>
  );
}

function App() {
  const [shows, setShows] = useState(loadShows);
  const [day, setDay] = useState(today);
  const [weekStart, setWeekStart] = useState(today);
  const [cinema, setCinema] = useState(1);
  const [screen, setScreen] = useState("all");
  const [query, setQuery] = useState("");
  const [view, setView] = useState("schedule");
  const [status, setStatus] = useState("SCHEDULED");
  const [selectedId, setSelectedId] = useState<number | null>(101);
  const [creating, setCreating] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [toast, setToast] = useState("");
  const filtered = shows
    .filter(
      (s) =>
        s.startTime.startsWith(day) &&
        s.cinemaId === cinema &&
        (screen === "all" || s.screenId === Number(screen)) &&
        (status === "all" || s.status === status) &&
        movies
          .find((m) => m.id === s.movieId)!
          .title.toLowerCase()
          .includes(query.toLowerCase()),
    )
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  const selected = filtered.find((s) => s.id === selectedId) ?? filtered[0];
  const dayShows = shows.filter(
    (s) =>
      s.startTime.startsWith(day) &&
      s.cinemaId === cinema &&
      s.status === "SCHEDULED",
  );
  const notice = (message: string) => setToast(message);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 4200);
    return () => clearTimeout(timer);
  }, [toast]);
  const updateShows = (next: Showtime[]) => {
    persist(next);
    setShows(next);
  };
  function cancel() {
    if (!selected) return;
    if (new Date(selected.startTime) <= new Date()) {
      notice("Suất chiếu đã bắt đầu, không thể hủy.");
      setConfirmCancel(false);
      return;
    }
    try {
      updateShows(
        shows.map((s) =>
          s.id === selected.id ? { ...s, status: "CANCELLED" } : s,
        ),
      );
    } catch {
      notice("Không lưu được thay đổi. Vui lòng cho phép lưu trữ và thử lại.");
      return;
    }
    setConfirmCancel(false);
    notice("Đã hủy suất chiếu. Khung giờ này đã được giải phóng.");
  }
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="eMovie Studio">
          <span className="brand-icon">
            <Film size={23} />
          </span>
          <span>
            eMovie<small>STUDIO</small>
          </span>
        </a>
        <div className="workspace-label">KHÔNG GIAN QUẢN TRỊ</div>
        <nav aria-label="Điều hướng chính">
          <button
            className={view === "schedule" ? "nav-item active" : "nav-item"}
            onClick={() => setView("schedule")}
          >
            <CalendarDays size={19} />
            Lịch chiếu
            <span className="nav-dot" />
          </button>
          <button
            className={view === "prices" ? "nav-item active" : "nav-item"}
            onClick={() => setView("prices")}
          >
            <Ticket size={19} />
            Giá vé
            <span className="nav-dot" />
          </button>
        </nav>
        <div className="sidebar-note">
          <span className="mini-star">✳</span>
          <p>
            Màn ảnh sáng.
            <br />
            Trải nghiệm trọn vẹn.
          </p>
          <small>Mỗi suất chiếu bắt đầu từ đây.</small>
        </div>
        <div className="sidebar-bottom">
          <div className="demo-label">
            <span />
            Bản demo tương tác
          </div>
          <p>Dữ liệu mẫu, lưu trên trình duyệt.</p>
          <div className="profile">
            <span className="avatar">CA</span>
            <div>
              Cinema Admin<small>Quản trị rạp chiếu</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Vận hành <span>/</span>{" "}
            <strong>{view === "schedule" ? "Lịch chiếu" : "Giá vé"}</strong>
          </div>
          <div className="topbar-right">
            <span className="live-dot" /> Studio đang sẵn sàng{" "}
            <span className="topbar-divider" />
            <span className="small-avatar">CA</span>
          </div>
        </header>
        <main>
          <section className="page-heading">
            <div>
              <div className="eyebrow">THE SHOW GOES ON</div>
              <h1>
                {view === "schedule" ? "Lịch chiếu" : "Giá vé & trải nghiệm"}
                <span>.</span>
              </h1>
              <p>Một lịch chiếu chỉn chu. Ngàn trải nghiệm đáng nhớ.</p>
            </div>
            <button className="primary" onClick={() => setCreating(true)}>
              <Plus size={18} />
              Tạo suất chiếu
            </button>
          </section>
          <section className="overview" aria-label="Tổng quan ngày">
            <div className="cinema-select">
              <span className="cinema-icon">
                <MapPin size={21} />
              </span>
              <div>
                <label htmlFor="cinema">RẠP ĐANG QUẢN LÝ</label>
                <select
                  id="cinema"
                  value={cinema}
                  onChange={(e) => setCinema(Number(e.target.value))}
                >
                  {cinemas.map((c) => (
                    <option value={c.id} key={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="stat">
              <strong>{String(dayShows.length).padStart(2, "0")}</strong>
              <span>
                suất chiếu
                <br />
                trong ngày
              </span>
            </div>
            <div className="stat">
              <strong>
                {String(new Set(dayShows.map((s) => s.movieId)).size).padStart(
                  2,
                  "0",
                )}
              </strong>
              <span>
                bộ phim
                <br />
                trên màn ảnh
              </span>
            </div>
            <div className="overview-caption">
              <Film size={20} />
              <span>
                Lights. Camera.
                <br />
                <b>A great movie night.</b>
              </span>
            </div>
          </section>
          <section className="schedule-workspace">
            <div className="schedule-panel">
              <div className="section-top">
                <h2>
                  {view === "schedule"
                    ? "Lịch chiếu trong tuần"
                    : "Chọn suất để chỉnh giá"}
                </h2>
                <label className="date-picker">
                  <CalendarDays size={15} />
                  <input
                    aria-label="Chọn ngày"
                    type="date"
                    value={day}
                    onChange={(e) => {
                      if (e.target.value) {
                        setDay(e.target.value);
                        setWeekStart(e.target.value);
                      }
                    }}
                  />
                </label>
              </div>
              <div className="week-picker">
                <button
                  className="week-arrow"
                  aria-label="Tuần trước"
                  onClick={() => {
                    const next = addDays(weekStart, -7);
                    setWeekStart(next);
                    setDay(next);
                  }}
                >
                  <ChevronLeft size={17} />
                </button>
                {Array.from({ length: 7 }, (_, i) => {
                  const date = addDays(weekStart, i);
                  const d = new Date(`${date}T12:00:00`);
                  return (
                    <button
                      key={date}
                      className={`day ${day === date ? "selected" : ""}`}
                      onClick={() => setDay(date)}
                      aria-pressed={day === date}
                    >
                      <span>
                        {date === today
                          ? "Hôm nay"
                          : [
                              "CN",
                              "Thứ 2",
                              "Thứ 3",
                              "Thứ 4",
                              "Thứ 5",
                              "Thứ 6",
                              "Thứ 7",
                            ][d.getDay()]}
                      </span>
                      <strong>{String(d.getDate()).padStart(2, "0")}</strong>
                      <i />
                    </button>
                  );
                })}
                <button
                  className="week-arrow"
                  aria-label="Tuần sau"
                  onClick={() => {
                    const next = addDays(weekStart, 7);
                    setWeekStart(next);
                    setDay(next);
                  }}
                >
                  <ChevronRight size={17} />
                </button>
              </div>
              <div className="filters">
                <label className="search">
                  <Search size={17} />
                  <input
                    placeholder="Tìm tên phim..."
                    aria-label="Tìm phim"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </label>
                <label className="screen-filter">
                  <SlidersHorizontal size={15} />
                  <select
                    aria-label="Lọc phòng"
                    value={screen}
                    onChange={(e) => setScreen(e.target.value)}
                  >
                    <option value="all">Tất cả phòng</option>
                    {[1, 2, 3].map((n) => (
                      <option value={n} key={n}>
                        Phòng {String(n).padStart(2, "0")}
                      </option>
                    ))}
                  </select>
                </label>
                <select
                  aria-label="Lọc trạng thái"
                  className="status-filter"
                  value={status}
                  onChange={(e) => setStatus(e.target.value)}
                >
                  <option value="SCHEDULED">Đã lên lịch</option>
                  <option value="CANCELLED">Đã hủy</option>
                  <option value="all">Tất cả</option>
                </select>
              </div>
              <div className="list-head">
                <span>PHIM / PHÒNG CHIẾU</span>
                <span>KHUNG GIỜ</span>
                <span>GIÁ TỪ</span>
                <span />
              </div>
              <div className="show-list">
                {filtered.map((show, index) => {
                  const movie = movies.find((m) => m.id === show.movieId)!;
                  return (
                    <button
                      style={{ animationDelay: `${index * 45}ms` }}
                      key={show.id}
                      className={`show-row ${selected?.id === show.id ? "chosen" : ""}`}
                      aria-label={`${movie.title}, ${time(show.startTime)}, phòng ${show.screenId}`}
                      aria-pressed={selected?.id === show.id}
                      onClick={() => setSelectedId(show.id)}
                    >
                      <div className="movie-cell">
                        <Poster movieId={show.movieId} />
                        <div>
                          <h3>{movie.title}</h3>
                          <p>
                            <span className="age">{movie.age}</span>
                            {movie.duration} phút{" "}
                            <span className="dot-separator">·</span> Phòng{" "}
                            {String(show.screenId).padStart(2, "0")}
                          </p>
                          <span
                            className={
                              show.status === "CANCELLED"
                                ? "row-status cancelled"
                                : "row-status"
                            }
                          >
                            <i />
                            {show.status === "CANCELLED"
                              ? "Đã hủy"
                              : "Đã lên lịch"}
                          </span>
                        </div>
                      </div>
                      <div className="time-cell">
                        <strong>{time(show.startTime)}</strong>
                        <span>{time(show.endTime)}</span>
                      </div>
                      <div className="price-cell">
                        <strong>
                          {money(show.basePrice)}
                          <small>đ</small>
                        </strong>
                        <span>
                          {show.prices.length ? "Theo loại ghế" : "Giá cơ bản"}
                        </span>
                      </div>
                      <ChevronRight className="row-arrow" size={18} />
                    </button>
                  );
                })}
                {!filtered.length && (
                  <div className="empty-state">
                    <Film size={36} />
                    <h3>Khung giờ còn rộng mở</h3>
                    <p>Chưa có suất chiếu phù hợp với bộ lọc này.</p>
                    <button
                      onClick={() => {
                        setQuery("");
                        setScreen("all");
                        setStatus("SCHEDULED");
                        setCreating(true);
                      }}
                      className="text-button"
                    >
                      Tạo suất chiếu mới <ArrowRight size={16} />
                    </button>
                  </div>
                )}
              </div>
              <div className="list-footer">
                <span>
                  {filtered.length} suất chiếu ·{" "}
                  {new Date(`${day}T12:00:00`).toLocaleDateString("vi-VN")}
                </span>
                <span>Giờ Việt Nam (GMT+7)</span>
              </div>
              <div className="editorial-note">
                <span className="note-line" />
                <p>“Điện ảnh là một giấc mơ mà ta cùng nhau mơ.”</p>
                <span className="note-line" />
              </div>
            </div>
            {selected ? (
              <PricePanel
                key={selected.id + JSON.stringify(selected.prices)}
                show={selected}
                onSave={(prices) => {
                  updateShows(
                    shows.map((s) =>
                      s.id === selected.id ? { ...s, prices } : s,
                    ),
                  );
                  notice(
                    prices.length
                      ? "Đã cập nhật bảng giá cho suất chiếu."
                      : "Đã áp dụng giá cơ bản cho tất cả loại ghế.",
                  );
                }}
                onCancel={() => setConfirmCancel(true)}
              />
            ) : (
              <aside className="price-panel no-selection">
                <Ticket size={32} />
                <h3>Bảng giá của bạn ở đây</h3>
                <p>Chọn một suất chiếu để xem và chỉnh giá theo loại ghế.</p>
              </aside>
            )}
          </section>
          <footer className="page-footer">
            <span>
              eMovie Studio <span>© 2026</span>
            </span>
            <span>Made for the big screen.</span>
          </footer>
        </main>
      </div>
      {toast && (
        <div className="toast" role="status">
          <Check size={18} />
          {toast}
          <button aria-label="Đóng thông báo" onClick={() => setToast("")}>
            <X size={16} />
          </button>
        </div>
      )}
      {creating && (
        <CreateDialog
          day={day}
          cinema={cinema}
          onClose={() => setCreating(false)}
          onCreate={(input) => {
            const created = createShow(shows, input);
            persist([...shows, created]);
            setShows([...shows, created]);
            setDay(created.startTime.slice(0, 10));
            setWeekStart(created.startTime.slice(0, 10));
            setSelectedId(created.id);
            setScreen("all");
            setQuery("");
            setStatus("SCHEDULED");
            setCreating(false);
            notice("Suất chiếu mới đã sẵn sàng.");
          }}
        />
      )}
      {confirmCancel && selected && (
        <Modal title="Hủy suất chiếu?" onClose={() => setConfirmCancel(false)}>
          <p className="modal-description">
            {movies.find((m) => m.id === selected.movieId)!.title} lúc{" "}
            {time(selected.startTime)} sẽ được đánh dấu đã hủy. Khung giờ này có
            thể dùng cho suất chiếu khác.
          </p>
          <div className="modal-actions">
            <button
              className="secondary"
              onClick={() => setConfirmCancel(false)}
            >
              Giữ lại
            </button>
            <button className="primary danger" onClick={cancel}>
              Xác nhận hủy
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function PricePanel({
  show,
  onSave,
  onCancel,
}: {
  show: Showtime;
  onSave: (prices: Showtime["prices"]) => void;
  onCancel: () => void;
}) {
  const movie = movies.find((m) => m.id === show.movieId)!;
  const [values, setValues] = useState<Record<number, string>>(() =>
    Object.fromEntries(
      seatTypes.map((type) => [
        type.id,
        String(show.prices.find((p) => p.seatTypeId === type.id)?.price ?? ""),
      ]),
    ),
  );
  const [error, setError] = useState("");
  const dirty = seatTypes.some(
    (type) =>
      values[type.id] !==
      String(show.prices.find((p) => p.seatTypeId === type.id)?.price ?? ""),
  );
  const save = (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const prices = seatTypes
        .filter((type) => values[type.id].trim() !== "")
        .map((type) => ({
          seatTypeId: type.id,
          price: Number(values[type.id]),
        }));
      validatePrices(prices);
      onSave(prices);
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <aside className="price-panel">
      <div className="panel-kicker">
        <span>
          <span className="live-dot" /> SUẤT CHIẾU ĐANG CHỌN
        </span>
        <span>#{show.id}</span>
      </div>
      <div className="feature-film">
        <Poster movieId={show.movieId} large />
        <div className="feature-overlay">
          <span>{movie.genre}</span>
          <h2>{movie.title}</h2>
          <p>{movie.subtitle}</p>
        </div>
      </div>
      <div className="panel-show-info">
        <span>
          <Clock3 size={15} />
          {time(show.startTime)} – {time(show.endTime)}
        </span>
        <span>
          <LayoutGrid size={15} />
          Phòng {String(show.screenId).padStart(2, "0")}
        </span>
      </div>
      <form onSubmit={save}>
        <div className="pricing-heading">
          <div>
            <h2>Bảng giá vé</h2>
            <p>Một chỗ ngồi. Một trải nghiệm.</p>
          </div>
          <Ticket size={22} />
        </div>
        <div className="base-price">
          <span>Giá cơ bản</span>
          <strong>
            {money(show.basePrice)} <small>đ</small>
          </strong>
        </div>
        <div className="price-fields">
          {seatTypes.map((type) => (
            <label className="price-field" key={type.id}>
              <span className={`seat-icon seat-${type.id}`}>
                {type.id === 3 ? <Users size={19} /> : <Ticket size={19} />}
              </span>
              <span className="seat-label">
                <strong>{type.name}</strong>
                <small>{type.description}</small>
              </span>
              <span className="price-input">
                <input
                  aria-label={`Giá ${type.name}`}
                  inputMode="decimal"
                  type="number"
                  min="0"
                  step="0.01"
                  max="9999999999.99"
                  placeholder={String(show.basePrice)}
                  value={values[type.id]}
                  onChange={(e) =>
                    setValues({ ...values, [type.id]: e.target.value })
                  }
                />
                <span>đ</span>
              </span>
            </label>
          ))}
        </div>
        <p className="price-help">
          <span>i</span>Để trống loại ghế để áp dụng giá cơ bản.
        </p>
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        <button className="primary save-button" disabled={!dirty} type="submit">
          {dirty ? "Lưu bảng giá" : "Bảng giá đã được lưu"}
          {dirty ? <ArrowRight size={17} /> : <Check size={17} />}
        </button>
        <button
          type="button"
          className="reset-prices"
          onClick={() => setValues({ 1: "", 2: "", 3: "" })}
        >
          Dùng giá cơ bản cho tất cả
        </button>
      </form>
      <div className="panel-footer">
        <span>
          {show.status === "CANCELLED"
            ? "Suất chiếu đã hủy"
            : "Lịch chiếu đã sẵn sàng"}
        </span>
        {show.status === "SCHEDULED" && (
          <button
            onClick={onCancel}
            disabled={new Date(show.startTime) <= new Date()}
          >
            Hủy suất chiếu
          </button>
        )}
      </div>
    </aside>
  );
}

function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    return () => ref.current?.close();
  }, []);
  return (
    <dialog ref={ref} onCancel={onClose} className="modal" aria-label={title}>
      <div className="modal-title">
        <div>
          <span className="eyebrow">EMOVIE STUDIO</span>
          <h2>{title}</h2>
        </div>
        <button className="icon-button" aria-label="Đóng" onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function CreateDialog({
  day,
  cinema,
  onClose,
  onCreate,
}: {
  day: string;
  cinema: number;
  onClose: () => void;
  onCreate: (input: Parameters<typeof createShow>[1]) => void;
}) {
  const [error, setError] = useState("");
  return (
    <Modal title="Một suất chiếu mới" onClose={onClose}>
      <p className="modal-description">
        Chọn phim, phòng và thời gian. Chúng ta lo phần còn lại.
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          try {
            onCreate({
              movieId: Number(data.get("movie")),
              cinemaId: cinema,
              screenId: Number(data.get("screen")),
              startTime: `${data.get("date")}T${data.get("time")}:00`,
              basePrice: Number(data.get("price")),
            });
          } catch (err) {
            setError((err as Error).message);
          }
        }}
      >
        <label className="form-field">
          Phim
          <select name="movie" required>
            {movies.map((m) => (
              <option key={m.id} value={m.id}>
                {m.title} · {m.duration} phút
              </option>
            ))}
          </select>
        </label>
        <div className="form-grid">
          <label className="form-field">
            Ngày chiếu
            <input
              name="date"
              type="date"
              min={today}
              defaultValue={day < today ? today : day}
              required
            />
          </label>
          <label className="form-field">
            Giờ bắt đầu
            <input name="time" type="time" defaultValue="18:00" required />
          </label>
          <label className="form-field">
            Phòng chiếu
            <select name="screen">
              {[1, 2, 3].map((n) => (
                <option key={n} value={n}>
                  Phòng {String(n).padStart(2, "0")}
                </option>
              ))}
            </select>
          </label>
          <label className="form-field">
            Giá cơ bản (đ)
            <input
              name="price"
              type="number"
              min="0"
              max="9999999999.99"
              step="0.01"
              defaultValue="90000"
              required
            />
          </label>
        </div>
        {error && (
          <div className="form-error" role="alert">
            {error}
          </div>
        )}
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onClose}>
            Để sau
          </button>
          <button className="primary" type="submit">
            <Plus size={17} />
            Tạo suất chiếu
          </button>
        </div>
      </form>
    </Modal>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
