import { Link, useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { useMovieCatalogue } from "./hooks";
import { MoviePoster } from "./MoviePoster";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
export function MoviesPage() {
  const [params, setParams] = useSearchParams();
  const rawPage = Number(params.get("page") ?? 0);
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const status =
    params.get("status") === "WILL_PRESENT" ? "WILL_PRESENT" : "PRESENTING";
  const keyword = params.get("q") ?? "";
  const query = useMovieCatalogue({ keyword, status, page, size: 12 });
  return (
    <main className="page cinema-page">
      <div className="catalogue-heading">
        <div>
          <p className="eyebrow">PHIM & LỊCH CHIẾU</p>
          <h1>Chọn phim. Chọn giờ của bạn.</h1>
          <p className="muted">Xem lịch chiếu và đặt ghế trực tiếp.</p>
        </div>
        <form
          className="search-form"
          onSubmit={(event) => {
            event.preventDefault();
            setParams({
              q: String(
                new FormData(event.currentTarget).get("q") ?? "",
              ).trim(),
              status,
            });
          }}
        >
          <Field label="Tìm phim">
            <input
              key={keyword}
              name="q"
              defaultValue={keyword}
              placeholder="Tên phim…"
              type="search"
            />
          </Field>
          <Button type="submit" aria-label="Tìm kiếm">
            <Search size={18} />
          </Button>
        </form>
      </div>
      <div className="tabs" aria-label="Danh mục phim">
        <Button
          aria-pressed={status === "PRESENTING"}
          onClick={() => setParams({ q: keyword, status: "PRESENTING" })}
        >
          Đang chiếu
        </Button>
        <Button
          aria-pressed={status === "WILL_PRESENT"}
          onClick={() => setParams({ q: keyword, status: "WILL_PRESENT" })}
        >
          Sắp chiếu
        </Button>
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
        <>
          <div className="movie-grid">
            {query.data.data.map((movie) => (
              <Link
                to={`/movies/${movie.id}`}
                className="movie-card"
                key={movie.id}
              >
                <MoviePoster title={movie.title} url={movie.posterUrl} />
                <div className="movie-card-info">
                  <span className="muted">
                    {movie.ageRating || "Chưa phân loại"} ·{" "}
                    {movie.durationMinutes} phút
                  </span>
                  <h2>{movie.title}</h2>
                  <span className="text-link">
                    Xem lịch chiếu <span aria-hidden="true">↗</span>
                  </span>
                </div>
              </Link>
            ))}
          </div>
          {!query.data.data.length && (
            <EmptyState
              title="Chưa có phim phù hợp"
              description="Thử tìm tên khác hoặc chuyển danh mục phim."
            />
          )}
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            change={(next) =>
              setParams({ q: keyword, status, page: String(next) })
            }
          />
        </>
      )}
    </main>
  );
}
