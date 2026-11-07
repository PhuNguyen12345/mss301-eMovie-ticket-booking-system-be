import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMovie } from "./hooks";
import { MoviePoster, safeWebUrl } from "./MoviePoster";
import { useShowtimes } from "../showtimes/hooks/useShowtimeQueries";
import { localDate, formatTime, formatMoney, hasStarted } from "../../lib/date";
import { Field } from "../../components/ui/Field";
import { Placeholder } from "../../components/ui/Placeholder";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
export function MoviePage() {
  const id = Number(useParams().movieId);
  const movie = useMovie(id);
  if (!Number.isSafeInteger(id) || id <= 0)
    return (
      <main className="page">
        <EmptyState title="Phim không hợp lệ" />
      </main>
    );
  return (
    <main className="page cinema-page">
      <Link to="/" className="text-link">
        ← Danh sách phim
      </Link>
      {movie.isPending ? (
        <Loading />
      ) : movie.isError ? (
        <ErrorMessage
          error={movie.error}
          retry={() => {
            void movie.refetch();
          }}
        />
      ) : (
        <>
          <section className="movie-detail">
            <MoviePoster title={movie.data.title} url={movie.data.posterUrl} />
            <div>
              <p className="eyebrow">
                {movie.data.status === "PRESENTING"
                  ? "ĐANG CHIẾU"
                  : movie.data.status === "WILL_PRESENT"
                    ? "SẮP CHIẾU"
                    : "ĐÃ NGỪNG CHIẾU"}
              </p>
              <h1>{movie.data.title}</h1>
              <p className="movie-meta">
                {movie.data.durationMinutes} phút ·{" "}
                {movie.data.ageRating || "Chưa phân loại"}
                {movie.data.language ? ` · ${movie.data.language}` : ""}
              </p>
              {!!movie.data.genres?.length && (
                <p className="muted">
                  {movie.data.genres.map((g) => g.name).join(" · ")}
                </p>
              )}
              <p className="movie-description">
                {movie.data.description || "Nội dung phim đang được cập nhật."}
              </p>
              {safeWebUrl(movie.data.trailerUrl) && (
                <a
                  className="button"
                  href={safeWebUrl(movie.data.trailerUrl)}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Xem trailer
                </a>
              )}
            </div>
          </section>
          <MovieShowtimes key={id} id={id} />
        </>
      )}
    </main>
  );
}
function MovieShowtimes({ id }: { id: number }) {
  const [date, setDate] = useState(localDate);
  const [page, setPage] = useState(0);
  const query = useShowtimes({
    movieId: id,
    date,
    status: "SCHEDULED",
    page,
    size: 20,
  });
  const rows =
    query.data?.data.filter((show) => !hasStarted(show.startTime)) ?? [];
  return (
    <section className="movie-schedule">
      <div className="section-top">
        <h2>Chọn suất chiếu</h2>
        <Field label="Ngày chiếu">
          <input
            type="date"
            min={localDate()}
            value={date}
            onChange={(e) => {
              if (e.target.value) {
                setDate(e.target.value);
                setPage(0);
              }
            }}
          />
        </Field>
      </div>
      <Placeholder
        compact
        title="Tìm theo rạp"
        description="Bộ lọc tên rạp đang được bổ sung. Bạn vẫn có thể chọn suất chiếu bên dưới."
      />
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
        <>
          <div className="sessions-grid">
            {rows.map((show) => (
              <Link
                className="session-card"
                to={`/showtimes/${show.id}/seats`}
                key={show.id}
              >
                <div>
                  <strong>{formatTime(show.startTime)}</strong>
                  <span> — {formatTime(show.endTime)}</span>
                </div>
                <p>
                  Rạp #{show.cinemaId} · Phòng #{show.screenId}
                </p>
                {show.cinemaLocation && <p>{show.cinemaLocation}</p>}
                <span className="muted">
                  Giá cơ bản {formatMoney(show.basePrice)}
                </span>
                <span className="text-link">Chọn ghế →</span>
              </Link>
            ))}
          </div>
          {!rows.length && (
            <EmptyState
              title="Chưa có suất chiếu phù hợp"
              description="Thử chọn ngày khác hoặc chuyển trang."
            />
          )}
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            change={setPage}
          />
        </>
      )}
    </section>
  );
}
