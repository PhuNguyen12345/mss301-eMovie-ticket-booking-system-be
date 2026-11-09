import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { useNavigationGuard } from "../../lib/useNavigationGuard";
import { useServerDraft } from "../../lib/useServerDraft";
import { useMovie } from "../movies/hooks";
import { MoviePoster } from "../movies/MoviePoster";
import { ReferencePicker } from "./ReferencePicker";
import { useMovieAssets, useMovieCredits, useMovieGenres } from "./hooks";
import { validatePoster, type Credit, type Reference } from "./api";

type Change = (active: boolean) => void;
function useDirtyState(active: boolean, change: Change) {
  useEffect(() => {
    change(active);
    return () => change(false);
  }, [active, change]);
}
function DraftActions({
  dirty,
  changed,
  busy,
  disabled,
  reset,
  label,
}: {
  dirty: boolean;
  changed: boolean;
  busy: boolean;
  disabled: boolean;
  reset: () => void;
  label: string;
}) {
  return (
    <>
      {changed && (
        <p className="notice" role="alert">
          Dữ liệu đã thay đổi ở nơi khác. Tải lại trước khi lưu để tránh ghi đè.
        </p>
      )}
      <div className="dialog-actions">
        <Button onClick={reset} disabled={busy || (!dirty && !changed)}>
          {changed ? "Tải lại dữ liệu" : "Bỏ thay đổi"}
        </Button>
        <Button
          type="submit"
          variant="primary"
          pending={busy}
          disabled={!dirty || changed || disabled}
        >
          {label}
        </Button>
      </div>
    </>
  );
}
export function MovieAssetsPage() {
  const id = Number(useParams().movieId);
  const { mode } = useSession();
  if (!Number.isSafeInteger(id) || id <= 0)
    return (
      <main className="page">
        <EmptyState title="Phim không hợp lệ" />
      </main>
    );
  if (mode === "demo")
    return (
      <main className="page">
        <Placeholder
          title="Chi tiết phim"
          description="Mở chế độ API để chỉnh thể loại, ê-kíp và poster."
        />
      </main>
    );
  return <MovieAssets key={id} id={id} />;
}
function MovieAssets({ id }: { id: number }) {
  const movie = useMovie(id);
  const genres = useMovieGenres(id);
  const credits = useMovieCredits(id);
  const [genreDirty, setGenreDirty] = useState(false);
  const [creditDirty, setCreditDirty] = useState(false);
  const [posterDirty, setPosterDirty] = useState(false);
  useNavigationGuard(genreDirty || creditDirty || posterDirty);
  return (
    <main className="page">
      <Link className="text-link" to="/admin/movies">
        ← Quản lý phim
      </Link>
      <div className="page-heading">
        <div>
          <h1>{movie.data?.title ?? "Chi tiết phim"}</h1>
          <p>Thể loại, ê-kíp và poster được lưu riêng.</p>
        </div>
      </div>
      <ErrorMessage
        error={movie.error}
        retry={() => {
          void movie.refetch();
        }}
      />
      <div className="movie-assets-grid">
        <section className="surface">
          <h2>Thể loại phim</h2>
          <ErrorMessage
            error={genres.error}
            retry={() => {
              void genres.refetch();
            }}
          />
          {genres.isPending ? (
            <Loading />
          ) : (
            genres.data && (
              <GenreEditor
                id={id}
                remote={genres.data}
                disabled={genres.isError}
                changed={setGenreDirty}
              />
            )
          )}
        </section>
        <section className="surface">
          <h2>Poster</h2>
          {movie.isPending ? (
            <Loading />
          ) : (
            movie.data && (
              <PosterEditor
                id={id}
                title={movie.data.title}
                url={movie.data.posterUrl}
                changed={setPosterDirty}
              />
            )
          )}
        </section>
        <section className="surface movie-credits-panel">
          <h2>Diễn viên & đạo diễn</h2>
          <ErrorMessage
            error={credits.error}
            retry={() => {
              void credits.refetch();
            }}
          />
          {credits.isPending ? (
            <Loading />
          ) : (
            credits.data && (
              <CreditsEditor
                id={id}
                remote={credits.data}
                disabled={credits.isError}
                changed={setCreditDirty}
              />
            )
          )}
        </section>
      </div>
    </main>
  );
}
function GenreEditor({
  id,
  remote,
  disabled,
  changed,
}: {
  id: number;
  remote: Reference[];
  disabled: boolean;
  changed: Change;
}) {
  const draft = useServerDraft(remote);
  const { genres: mutation } = useMovieAssets(id);
  const [saved, setSaved] = useState(false);
  useDirtyState(draft.dirty || mutation.isPending, changed);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setSaved(false);
        try {
          draft.reset(
            await mutation.mutateAsync(draft.value.map((row) => row.id)),
          );
          setSaved(true);
        } catch {
          /* Keep edits after failed saves. */
        }
      }}
    >
      <fieldset disabled={mutation.isPending}>
        <ReferencePicker
          kind="genres"
          excluded={draft.value.map((row) => row.id)}
          add={(row) => {
            draft.setValue((previous) => [...previous, row]);
            setSaved(false);
          }}
        />
        <div className="selected-references">
          {draft.value.map((row) => (
            <div className="selected-reference" key={row.id}>
              <span>{row.name}</span>
              <Button
                aria-label={`Bỏ thể loại ${row.name}`}
                onClick={() => {
                  draft.setValue((previous) =>
                    previous.filter((item) => item.id !== row.id),
                  );
                  setSaved(false);
                }}
              >
                Bỏ
              </Button>
            </div>
          ))}
        </div>
        {!draft.value.length && (
          <p className="muted">Phim chưa được gán thể loại.</p>
        )}
      </fieldset>
      <ErrorMessage error={mutation.error} />
      {saved && !draft.dirty && <p role="status">Đã lưu thể loại.</p>}
      <DraftActions
        dirty={draft.dirty}
        changed={draft.remoteChanged}
        busy={mutation.isPending}
        disabled={disabled}
        reset={() => {
          draft.reset();
          mutation.reset();
          setSaved(false);
        }}
        label="Lưu thể loại"
      />
    </form>
  );
}
type CreditDraft = Omit<Credit, "id">;
function CreditsEditor({
  id,
  remote,
  disabled,
  changed,
}: {
  id: number;
  remote: Credit[];
  disabled: boolean;
  changed: Change;
}) {
  const draft = useServerDraft<CreditDraft[]>(
    remote.map(({ id: _id, ...row }) => row),
  );
  const { credits: mutation } = useMovieAssets(id);
  const [role, setRole] = useState<Credit["creditType"]>("ACTOR");
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  useDirtyState(draft.dirty || mutation.isPending, changed);
  function update(index: number, patch: Partial<CreditDraft>) {
    setSaved(false);
    draft.setValue((previous) =>
      previous.map((row, i) => (i === index ? { ...row, ...patch } : row)),
    );
  }
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setSaved(false);
        setError(null);
        try {
          const result = await mutation.mutateAsync(
            draft.value.map(({ fullName: _name, ...row }) => ({
              ...row,
              characterName:
                row.creditType === "DIRECTOR"
                  ? null
                  : row.characterName?.trim() || null,
            })),
          );
          draft.reset(result.map(({ id: _id, ...row }) => row));
          setSaved(true);
        } catch (error) {
          setError(error);
        }
      }}
    >
      <fieldset disabled={mutation.isPending}>
        <Field label="Vai trò thêm vào phim">
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as Credit["creditType"])}
          >
            <option value="ACTOR">Diễn viên</option>
            <option value="DIRECTOR">Đạo diễn</option>
          </select>
        </Field>
        <ReferencePicker
          kind="persons"
          excluded={draft.value
            .filter((row) => row.creditType === role)
            .map((row) => row.personId)}
          add={(person) => {
            setSaved(false);
            draft.setValue((previous) => [
              ...previous,
              {
                personId: person.id,
                fullName: person.name,
                creditType: role,
                characterName: null,
                displayOrder: previous.length + 1,
              },
            ]);
          }}
        />
        <div className="credits-editor">
          {draft.value.map((row, index) => (
            <div
              className="credit-row"
              key={`${row.personId}:${row.creditType}`}
            >
              <div>
                <strong>{row.fullName}</strong>
                <p className="muted">
                  {row.creditType === "DIRECTOR" ? "Đạo diễn" : "Diễn viên"}
                </p>
              </div>
              {row.creditType === "ACTOR" ? (
                <Field label={`Vai diễn của ${row.fullName}`}>
                  <input
                    maxLength={255}
                    value={row.characterName ?? ""}
                    onChange={(e) =>
                      update(index, { characterName: e.target.value })
                    }
                  />
                </Field>
              ) : (
                <span />
              )}
              <Field
                label={`Thứ tự ${row.fullName} (${row.creditType === "DIRECTOR" ? "đạo diễn" : "diễn viên"})`}
              >
                <input
                  type="number"
                  min={1}
                  step={1}
                  max={2147483647}
                  value={row.displayOrder || ""}
                  required
                  onChange={(e) =>
                    update(index, { displayOrder: Number(e.target.value) })
                  }
                />
              </Field>
              <Button
                aria-label={`Bỏ ${row.fullName} (${row.creditType})`}
                onClick={() => {
                  draft.setValue((previous) =>
                    previous.filter((_, i) => i !== index),
                  );
                  setSaved(false);
                }}
              >
                Bỏ
              </Button>
            </div>
          ))}
        </div>
        {!draft.value.length && (
          <p className="muted">Chưa có thông tin ê-kíp.</p>
        )}
      </fieldset>
      <ErrorMessage error={error} />
      {saved && !draft.dirty && <p role="status">Đã lưu ê-kíp.</p>}
      <DraftActions
        dirty={draft.dirty}
        changed={draft.remoteChanged}
        busy={mutation.isPending}
        disabled={disabled}
        reset={() => {
          draft.reset();
          setError(null);
          setSaved(false);
        }}
        label="Lưu ê-kíp"
      />
    </form>
  );
}
function PosterEditor({
  id,
  title,
  url,
  changed,
}: {
  id: number;
  title: string;
  url?: string | null;
  changed: Change;
}) {
  const { poster: mutation } = useMovieAssets(id);
  const [mode, setMode] = useState<"file" | "url">("file");
  const [file, setFile] = useState<File>();
  const [source, setSource] = useState("");
  const [preview, setPreview] = useState<string>();
  const [error, setError] = useState<unknown>(null);
  const [version, setVersion] = useState(0);
  useDirtyState(!!file || !!source || mutation.isPending, changed);
  useEffect(() => {
    if (!file) {
      setPreview(undefined);
      return;
    }
    const local = URL.createObjectURL(file);
    setPreview(local);
    return () => URL.revokeObjectURL(local);
  }, [file]);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        try {
          if (mode === "file" && !file)
            throw new Error("Vui lòng chọn ảnh poster.");
          await mutation.mutateAsync(mode === "file" ? file! : source.trim());
          setFile(undefined);
          setSource("");
          setVersion((value) => value + 1);
        } catch (error) {
          setError(error);
        }
      }}
    >
      <div className="poster-editor-preview">
        {preview ? (
          <img src={preview} alt="Poster đã chọn, chưa lưu" />
        ) : (
          <MoviePoster title={title} url={url} />
        )}
      </div>
      <fieldset disabled={mutation.isPending}>
        <Field label="Nguồn poster">
          <select
            value={mode}
            onChange={(e) => {
              setMode(e.target.value as "file" | "url");
              setFile(undefined);
              setSource("");
              setError(null);
              mutation.reset();
            }}
          >
            <option value="file">Tải ảnh từ máy</option>
            <option value="url">Nhập ảnh từ đường dẫn</option>
          </select>
        </Field>
        {mode === "file" ? (
          <Field label="Ảnh poster" hint="JPG hoặc PNG; tối đa 5 MB.">
            <input
              key={version}
              type="file"
              accept="image/jpeg,image/png"
              onChange={(e) => {
                setError(null);
                mutation.reset();
                const selected = e.target.files?.[0];
                setFile(undefined);
                if (selected) {
                  try {
                    validatePoster(selected);
                    setFile(selected);
                  } catch (error) {
                    setError(error);
                    e.target.value = "";
                  }
                }
              }}
            />
          </Field>
        ) : (
          <Field label="Đường dẫn ảnh nguồn">
            <input
              type="url"
              value={source}
              onChange={(e) => {
                setSource(e.target.value);
                mutation.reset();
              }}
              required
            />
          </Field>
        )}
      </fieldset>
      <ErrorMessage error={error} />
      {mutation.isSuccess && <p role="status">Đã cập nhật poster.</p>}
      <div className="dialog-actions">
        <Button
          disabled={mutation.isPending || (!file && !source)}
          onClick={() => {
            setFile(undefined);
            setSource("");
            setVersion((value) => value + 1);
            setError(null);
            mutation.reset();
          }}
        >
          Bỏ thay đổi
        </Button>
        <Button
          variant="primary"
          type="submit"
          pending={mutation.isPending}
          disabled={mode === "file" ? !file : !source.trim()}
        >
          Lưu poster
        </Button>
      </div>
    </form>
  );
}
