import { ChevronLeft, ChevronRight } from "lucide-react";
import { VenueName } from "../../cinema/VenueName";
import { Button } from "../../../components/ui/Button";
import { EmptyState } from "../../../components/ui/Feedback";
import { formatTime, formatMoney } from "../../../lib/date";
import type { Movie, Page, Showtime } from "../model/contracts";

export function ShowtimeList({
  page,
  movies,
  selectedId,
  select,
  changePage,
}: {
  page: Page<Showtime>;
  movies: Movie[];
  selectedId?: number;
  select: (id: number) => void;
  changePage: (page: number) => void;
}) {
  const byId = new Map(movies.map((movie) => [movie.id, movie]));
  return (
    <>
      <div className="list-head">
        <span>Phim / phòng chiếu</span>
        <span>Khung giờ</span>
        <span>Giá cơ bản</span>
      </div>
      <div className="show-list">
        {page.data.map((show) => {
          const movie = byId.get(show.movieId);
          return (
            <button
              key={show.id}
              className={`show-row ${selectedId === show.id ? "chosen" : ""}`}
              aria-label={`${movie?.title ?? `Phim #${show.movieId}`}, ${formatTime(show.startTime)}, phòng ${show.screenId}`}
              aria-pressed={selectedId === show.id}
              onClick={() => select(show.id)}
            >
              <div className="movie-cell">
                <h3>{movie?.title ?? `Phim #${show.movieId}`}</h3>
                <p>
                  <VenueName
                    cinemaId={show.cinemaId}
                    screenId={show.screenId}
                  />
                  {movie ? ` · ${movie.durationMinutes} phút` : ""}
                </p>
                <span
                  className={`badge ${show.status === "CANCELLED" ? "badge-cancelled" : ""}`}
                >
                  {show.status === "CANCELLED" ? "Đã hủy" : "Đã lên lịch"}
                </span>
              </div>
              <div className="time-cell">
                <strong>{formatTime(show.startTime)}</strong>
                <span>
                  {formatTime(show.endTime)}
                  {show.endTime.slice(0, 10) !== show.startTime.slice(0, 10)
                    ? " (+1 ngày)"
                    : ""}
                </span>
              </div>
              <span className="price-cell">{formatMoney(show.basePrice)}</span>
            </button>
          );
        })}
      </div>
      {!page.data.length && (
        <EmptyState
          title="Không có suất chiếu"
          description="Thử chọn ngày khác hoặc thay đổi bộ lọc."
        />
      )}
      <div className="list-footer">
        <span>{page.totalElements} suất chiếu</span>
        {page.totalPages > 1 && (
          <div className="pagination">
            <Button
              aria-label="Trang trước"
              disabled={page.first}
              onClick={() => changePage(page.page - 1)}
            >
              <ChevronLeft size={16} />
            </Button>
            <span>
              {page.page + 1} / {page.totalPages}
            </span>
            <Button
              aria-label="Trang sau"
              disabled={page.last}
              onClick={() => changePage(page.page + 1)}
            >
              <ChevronRight size={16} />
            </Button>
          </div>
        )}
      </div>
    </>
  );
}
