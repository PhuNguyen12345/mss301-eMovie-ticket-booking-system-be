import { useState } from "react";
import { Link } from "react-router-dom";
import { MovieAdminNav } from "../movie-management/MovieAdminNav";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useServices } from "../../app/ServicesProvider";
import { useMovieCatalogue, movieKeys } from "../movies/hooks";
import type { Movie, MovieInput } from "../movies/api";
import { MoviePoster } from "../movies/MoviePoster";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { Dialog } from "../../components/ui/Dialog";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
import { Placeholder } from "../../components/ui/Placeholder";
export function AdminMoviesPage() {
  const { showtimes } = useServices();
  const [filters, setFilters] = useState({
    keyword: "",
    status: "",
    page: 0,
    size: 12,
  });
  const query = useMovieCatalogue(filters);
  const [edit, setEdit] = useState<Movie | "create" | null>(null);
  return (
    <main className="page">
      <MovieAdminNav />
      <div className="page-heading">
        <div>
          <h1>Quản lý phim</h1>
          <p>Thông tin phim, thời lượng và trạng thái phát hành.</p>
        </div>
        <Button
          variant="primary"
          disabled={showtimes.mode !== "api"}
          onClick={() => setEdit("create")}
        >
          Thêm phim
        </Button>
      </div>
      {showtimes.mode === "demo" && (
        <Placeholder
          compact
          title="Chỉnh sửa danh mục phim"
          description="Chức năng ghi dữ liệu khả dụng khi kết nối dịch vụ phim."
        />
      )}
      <form
        className="admin-filters"
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          setFilters({
            ...filters,
            keyword: String(form.get("q")).trim(),
            status: String(form.get("status")),
            page: 0,
          });
        }}
      >
        <Field label="Tên phim">
          <input name="q" type="search" />
        </Field>
        <Field label="Trạng thái phim">
          <select name="status">
            <option value="">Tất cả</option>
            <option value="PRESENTING">Đang chiếu</option>
            <option value="WILL_PRESENT">Sắp chiếu</option>
            <option value="EXPIRED">Ngừng chiếu</option>
          </select>
        </Field>
        <Button type="submit">Tìm kiếm</Button>
      </form>
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
          <div className="admin-movie-list">
            {query.data.data.map((movie) => (
              <article className="admin-movie-row" key={movie.id}>
                <MoviePoster title={movie.title} url={movie.posterUrl} />
                <div>
                  <h2>{movie.title}</h2>
                  <p className="muted">
                    {movie.durationMinutes} phút ·{" "}
                    {movie.ageRating || "Chưa phân loại"}
                  </p>
                  <span className="badge">
                    {movie.status === "PRESENTING"
                      ? "Đang chiếu"
                      : movie.status === "WILL_PRESENT"
                        ? "Sắp chiếu"
                        : "Ngừng chiếu"}
                  </span>
                </div>
                <div className="record-actions">
                  <Button
                    disabled={showtimes.mode !== "api"}
                    onClick={() => setEdit(movie)}
                  >
                    Chỉnh sửa
                  </Button>
                  <Link className="button" to={`/admin/movies/${movie.id}`}>
                    Thể loại, ê-kíp & poster
                  </Link>
                </div>
              </article>
            ))}
          </div>
          {!query.data.data.length && (
            <EmptyState title="Chưa có phim phù hợp" />
          )}
          <Pagination
            page={filters.page}
            totalPages={query.data.totalPages}
            change={(page) => setFilters({ ...filters, page })}
          />
        </>
      )}
      {edit && (
        <MovieDialog
          movie={edit === "create" ? undefined : edit}
          close={() => setEdit(null)}
        />
      )}
    </main>
  );
}
function MovieDialog({ movie, close }: { movie?: Movie; close: () => void }) {
  const { movies } = useServices();
  const cache = useQueryClient();
  const mutation = useMutation({
    mutationFn: (input: MovieInput) => movies.save(input, movie?.id),
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: movieKeys.all }),
        cache.invalidateQueries({ queryKey: ["movies"] }),
      ]);
      close();
    },
  });
  const fields = [
    { name: "title", label: "Tên phim", value: movie?.title, required: true },
    { name: "language", label: "Ngôn ngữ", value: movie?.language },
    { name: "ageRating", label: "Phân loại độ tuổi", value: movie?.ageRating },
    {
      name: "posterUrl",
      label: "Đường dẫn poster",
      value: movie?.posterUrl,
      type: "url",
    },
    {
      name: "trailerUrl",
      label: "Đường dẫn trailer",
      value: movie?.trailerUrl,
      type: "url",
    },
  ];
  return (
    <Dialog
      title={movie ? "Chỉnh sửa phim" : "Thêm phim"}
      onClose={close}
      busy={mutation.isPending}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          const optional = (name: string) =>
            String(form.get(name) ?? "").trim() || null;
          mutation.mutate({
            title: String(form.get("title")).trim(),
            description: optional("description"),
            durationMinutes: Number(form.get("duration")),
            releaseDate: optional("releaseDate"),
            language: optional("language"),
            ageRating: optional("ageRating"),
            posterUrl: optional("posterUrl"),
            trailerUrl: optional("trailerUrl"),
            status: String(form.get("status")) as Movie["status"],
          });
        }}
      >
        <fieldset disabled={mutation.isPending}>
          {fields.map((field) => (
            <Field label={field.label} key={field.name}>
              <input
                name={field.name}
                type={field.type || "text"}
                maxLength={
                  field.name === "title"
                    ? 255
                    : field.name === "language"
                      ? 50
                      : field.name === "ageRating"
                        ? 20
                        : 500
                }
                defaultValue={field.value || ""}
                required={field.required}
              />
            </Field>
          ))}
          <Field label="Nội dung phim">
            <textarea
              name="description"
              defaultValue={movie?.description || ""}
              rows={4}
            />
          </Field>
          <div className="form-grid">
            <Field label="Thời lượng (phút)">
              <input
                name="duration"
                type="number"
                min="1"
                max="1440"
                step="1"
                required
                defaultValue={movie?.durationMinutes ?? 120}
              />
            </Field>
            <Field label="Ngày phát hành">
              <input
                name="releaseDate"
                type="date"
                required
                defaultValue={movie?.releaseDate || ""}
              />
            </Field>
          </div>
          <Field label="Trạng thái">
            <select
              name="status"
              defaultValue={movie?.status ?? "WILL_PRESENT"}
            >
              <option value="WILL_PRESENT">Sắp chiếu</option>
              <option value="PRESENTING">Đang chiếu</option>
              <option value="EXPIRED">Ngừng chiếu</option>
            </select>
          </Field>
        </fieldset>
        <ErrorMessage error={mutation.error} />
        <div className="dialog-actions">
          <Button onClick={close} disabled={mutation.isPending}>
            Đóng
          </Button>
          <Button variant="primary" type="submit" pending={mutation.isPending}>
            Lưu phim
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
