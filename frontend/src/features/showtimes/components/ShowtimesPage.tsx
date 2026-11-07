import { useState } from "react";
import { Plus, RefreshCw } from "lucide-react";
import { useSession } from "../../../app/ServicesProvider";
import { useNavigationGuard } from "../../../lib/useNavigationGuard";
import { Button } from "../../../components/ui/Button";
import { Field } from "../../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
  Toast,
} from "../../../components/ui/Feedback";
import { useToast } from "../../../lib/useToast";
import { useShowtimeFilters } from "../hooks/useShowtimeFilters";
import { useMovies, useShowtimes } from "../hooks/useShowtimeQueries";
import { CreateShowtimeDialog } from "./CreateShowtimeDialog";
import { CancelShowtimeDialog } from "./CancelShowtimeDialog";
import { DateStrip } from "./DateStrip";
import { ShowtimeFilters } from "./ShowtimeFilters";
import { ShowtimeList } from "./ShowtimeList";
import { PricePanel } from "./PricePanel";

export function ShowtimesPage() {
  const session = useSession();
  const calendar = useShowtimeFilters();
  const toast = useToast();
  const list = useShowtimes(calendar.filters);
  const movies = useMovies();
  const [selectedId, setSelectedId] = useState<number>();
  const [editorVersion, setEditorVersion] = useState(0);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<"create" | "cancel" | "edit" | null>(
    null,
  );
  const selected =
    list.data?.data.find((show) => show.id === selectedId) ??
    list.data?.data[0];
  const selectedMovie = movies.data?.find(
    (movie) => movie.id === selected?.movieId,
  );
  useNavigationGuard(dirty || busy);
  function navigate(action: () => void) {
    if (busy) return;
    if (dirty && !window.confirm("Bỏ thay đổi giá chưa lưu?")) return;
    if (dirty) setEditorVersion((value) => value + 1);
    setDirty(false);
    action();
  }
  return (
    <>
      <main className="page">
        <div className="page-heading">
          <div>
            <h1>Lịch chiếu</h1>
            <p>Quản lý lịch chiếu và giá vé theo loại ghế.</p>
          </div>
          <Button
            variant="primary"
            disabled={!session.canWrite || busy || !movies.data?.length}
            onClick={() => navigate(() => setDialog("create"))}
          >
            <Plus size={17} />
            Tạo suất chiếu
          </Button>
        </div>
        <ErrorMessage
          error={movies.error}
          retry={() => {
            void movies.refetch();
          }}
        />
        <div className="schedule-workspace">
          <section className="schedule-panel" aria-label="Danh sách suất chiếu">
            <fieldset disabled={busy}>
              <div className="section-top">
                <Field label="Ngày chiếu">
                  <input
                    type="date"
                    value={calendar.filters.date}
                    onChange={(e) =>
                      navigate(() => calendar.selectDate(e.target.value))
                    }
                  />
                </Field>
                <Button
                  variant="ghost"
                  aria-label="Làm mới lịch chiếu"
                  disabled={list.isFetching}
                  onClick={() =>
                    navigate(() => {
                      void list.refetch();
                    })
                  }
                >
                  <RefreshCw size={16} />
                </Button>
              </div>
              <DateStrip
                value={calendar.filters.date}
                start={calendar.weekStart}
                select={(date) => navigate(() => calendar.selectDate(date))}
                move={(offset) => navigate(() => calendar.moveWeek(offset))}
              />
              <ShowtimeFilters
                value={calendar.filters}
                movies={movies.data ?? []}
                update={(patch) => navigate(() => calendar.update(patch))}
              />
            </fieldset>
            {list.isPending ? (
              <Loading />
            ) : list.isError ? (
              <ErrorMessage
                error={list.error}
                retry={() => {
                  void list.refetch();
                }}
              />
            ) : (
              <ShowtimeList
                page={list.data}
                movies={movies.data ?? []}
                selectedId={selected?.id}
                select={(id) => {
                  if (id !== selected?.id) navigate(() => setSelectedId(id));
                }}
                changePage={(page) => navigate(() => calendar.update({ page }))}
              />
            )}
          </section>
          {selected ? (
            <PricePanel
              key={`${selected.id}:${editorVersion}`}
              show={selected}
              movie={selectedMovie}
              canWrite={session.canWrite}
              onDirtyChange={setDirty}
              onBusyChange={setBusy}
              notify={toast.notify}
              onCancel={() => navigate(() => setDialog("cancel"))}
              onEdit={() => navigate(() => setDialog("edit"))}
            />
          ) : (
            <aside className="price-panel no-selection">
              <EmptyState
                title="Chưa chọn suất chiếu"
                description="Chọn một suất chiếu để xem bảng giá."
              />
            </aside>
          )}
        </div>
      </main>
      <Toast message={toast.message} dismiss={toast.dismiss} />
      {(dialog === "create" || dialog === "edit") && session.canWrite && (
        <CreateShowtimeDialog
          filters={calendar.filters}
          show={dialog === "edit" ? selected : undefined}
          movies={movies.data ?? []}
          close={() => setDialog(null)}
          created={(show) => {
            calendar.selectDate(show.startTime.slice(0, 10));
            calendar.update({
              movieId: undefined,
              location: undefined,
              screenId: undefined,
              cinemaId: show.cinemaId,
              status: "SCHEDULED",
            });
            setSelectedId(show.id);
            setDialog(null);
            toast.notify(
              dialog === "edit"
                ? "Đã cập nhật suất chiếu."
                : "Đã tạo suất chiếu.",
            );
          }}
        />
      )}
      {dialog === "cancel" && selected && session.canWrite && (
        <CancelShowtimeDialog
          show={selected}
          title={selectedMovie?.title ?? `Phim #${selected.movieId}`}
          close={() => setDialog(null)}
          cancelled={() => {
            setDialog(null);
            toast.notify("Đã hủy suất chiếu.");
          }}
        />
      )}
    </>
  );
}
