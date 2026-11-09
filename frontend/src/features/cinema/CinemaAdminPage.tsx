import { useState, type ReactNode } from "react";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Field } from "../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import {
  useCinemas,
  useScreens,
  usePhysicalSeats,
  useSeatTypes,
  useCinemaMutation,
  type CinemaCommand,
} from "./hooks";
import type { Cinema, Screen, SeatType, FacilityStatus } from "./api";

const statusNames: Record<FacilityStatus, string> = {
  ACTIVE: "Hoạt động",
  INACTIVE: "Ngừng hoạt động",
  MAINTENANCE: "Bảo trì",
};
type Editor =
  | { kind: "cinema"; value?: Cinema }
  | { kind: "screen"; cinemaId: number; value?: Screen }
  | { kind: "seat-type"; value?: SeatType }
  | { kind: "seat"; screenId: number }
  | {
      kind: "status";
      target: "cinema" | "screen" | "seat";
      id: number;
      name: string;
      status: FacilityStatus;
    };

function RecordList<
  T extends { id: number; name: string; code: string; status?: FacilityStatus },
>({
  rows,
  selected,
  select,
  edit,
  status,
}: {
  rows: T[];
  selected?: number;
  select?: (id: number) => void;
  edit: (row: T) => void;
  status?: (row: T) => void;
}) {
  return (
    <div className="catalogue-records">
      {rows.map((row) => (
        <div
          className={`catalogue-record ${selected === row.id ? "chosen" : ""}`}
          key={row.id}
        >
          {select ? (
            <button
              type="button"
              className="record-select"
              aria-pressed={selected === row.id}
              onClick={() => select(row.id)}
            >
              <strong>{row.name}</strong>
              <small>{row.code}</small>
            </button>
          ) : (
            <div>
              <strong>{row.name}</strong>
              <small>{row.code}</small>
            </div>
          )}
          <div className="record-actions">
            {row.status && (
              <span className="badge">{statusNames[row.status]}</span>
            )}
            <Button aria-label={`Sửa ${row.name}`} onClick={() => edit(row)}>
              Sửa
            </Button>
            {status && (
              <Button
                aria-label={`Trạng thái ${row.name}`}
                onClick={() => status(row)}
              >
                Trạng thái
              </Button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
function Section({
  title,
  add,
  children,
}: {
  title: string;
  add: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="surface catalogue-section">
      <div className="section-top">
        <h2>{title}</h2>
        {add}
      </div>
      {children}
    </section>
  );
}
export function CinemaAdminPage() {
  const { mode } = useSession();
  if (mode === "demo")
    return (
      <main className="page">
        <h1>Rạp & phòng</h1>
        <Placeholder
          title="Quản lý rạp và sơ đồ ghế"
          description="Mở chế độ kết nối API để quản lý danh mục thật."
        />
      </main>
    );
  return <CinemaManagement />;
}
function CinemaManagement() {
  const cinemas = useCinemas();
  const types = useSeatTypes();
  const [cinemaId, setCinemaId] = useState<number>();
  const [screenId, setScreenId] = useState<number>();
  const [editor, setEditor] = useState<Editor | null>(null);
  const cinema =
    cinemas.data?.find((c) => c.id === cinemaId) ?? cinemas.data?.[0];
  const screens = useScreens(cinema?.id);
  const screen =
    screens.data?.find((s) => s.id === screenId) ?? screens.data?.[0];
  const seats = usePhysicalSeats(screen?.id);
  const parentActive =
    cinema?.status === "ACTIVE" && screen?.status === "ACTIVE";
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Rạp & phòng</h1>
          <p>Danh mục rạp, phòng chiếu, loại ghế và sơ đồ ghế.</p>
        </div>
      </div>
      <div className="catalogue-admin-grid">
        <Section
          title="Rạp chiếu"
          add={
            <Button onClick={() => setEditor({ kind: "cinema" })}>
              Thêm rạp
            </Button>
          }
        >
          <ErrorMessage
            error={cinemas.error}
            retry={() => {
              void cinemas.refetch();
            }}
          />
          {cinemas.isPending ? (
            <Loading />
          ) : (
            <RecordList
              rows={cinemas.data ?? []}
              selected={cinema?.id}
              select={(id) => {
                setCinemaId(id);
                setScreenId(undefined);
              }}
              edit={(value) => setEditor({ kind: "cinema", value })}
              status={(row) =>
                setEditor({ kind: "status", target: "cinema", ...row })
              }
            />
          )}
          {cinemas.data?.length === 0 && <EmptyState title="Chưa có rạp" />}
        </Section>
        <Section
          title={cinema ? `Phòng · ${cinema.name}` : "Phòng chiếu"}
          add={
            <Button
              disabled={!cinema}
              onClick={() =>
                setEditor({ kind: "screen", cinemaId: cinema!.id })
              }
            >
              Thêm phòng
            </Button>
          }
        >
          <ErrorMessage
            error={screens.error}
            retry={() => {
              void screens.refetch();
            }}
          />
          {cinema && screens.isPending ? (
            <Loading />
          ) : (
            <RecordList
              rows={screens.data ?? []}
              selected={screen?.id}
              select={setScreenId}
              edit={(value) =>
                setEditor({ kind: "screen", cinemaId: value.cinemaId, value })
              }
              status={(row) =>
                setEditor({ kind: "status", target: "screen", ...row })
              }
            />
          )}
          {!screens.data?.length && !screens.isFetching && (
            <EmptyState title="Chưa có phòng" />
          )}
        </Section>
      </div>
      <Section
        title="Loại ghế"
        add={
          <Button onClick={() => setEditor({ kind: "seat-type" })}>
            Thêm loại ghế
          </Button>
        }
      >
        <ErrorMessage
          error={types.error}
          retry={() => {
            void types.refetch();
          }}
        />
        {types.isPending ? (
          <Loading />
        ) : (
          <RecordList
            rows={types.data ?? []}
            edit={(value) => setEditor({ kind: "seat-type", value })}
          />
        )}
      </Section>
      <Section
        title={screen ? `Ghế · ${screen.name}` : "Sơ đồ ghế"}
        add={
          <Button
            disabled={!screen || !types.data?.length}
            onClick={() => setEditor({ kind: "seat", screenId: screen!.id })}
          >
            Thêm ghế
          </Button>
        }
      >
        <ErrorMessage
          error={seats.error}
          retry={() => {
            void seats.refetch();
          }}
        />
        {screen && !parentActive && (
          <p className="notice">
            Rạp hoặc phòng đang ngừng hoạt động. Bật lại rạp và phòng trước khi
            chỉnh trạng thái từng ghế.
          </p>
        )}
        {screen && seats.isPending ? (
          <Loading />
        ) : (
          <div className="physical-seats">
            {seats.data?.map((seat) => (
              <button
                className={`physical-seat ${seat.status === "ACTIVE" ? "" : "inactive"}`}
                key={seat.id}
                type="button"
                disabled={!parentActive}
                onClick={() =>
                  setEditor({
                    kind: "status",
                    target: "seat",
                    id: seat.id,
                    name: `Ghế ${seat.rowCode}${seat.seatNumber}`,
                    status: seat.status,
                  })
                }
                aria-label={`Ghế ${seat.rowCode}${seat.seatNumber}: ${statusNames[seat.status]}`}
              >
                <strong>
                  {seat.rowCode}
                  {seat.seatNumber}
                </strong>
                <span>
                  {types.data?.find((t) => t.id === seat.seatTypeId)?.name ??
                    `Loại #${seat.seatTypeId}`}
                </span>
                <small>{statusNames[seat.status]}</small>
              </button>
            ))}
          </div>
        )}
        {!seats.data?.length && !seats.isFetching && (
          <EmptyState title="Chưa có ghế" />
        )}
      </Section>
      {editor && (
        <CatalogueDialog
          editor={editor}
          types={types.data ?? []}
          close={() => setEditor(null)}
        />
      )}
    </main>
  );
}
function CatalogueDialog({
  editor,
  types,
  close,
}: {
  editor: Editor;
  types: SeatType[];
  close: () => void;
}) {
  const mutation = useCinemaMutation();
  const value = "value" in editor ? editor.value : undefined;
  const title =
    editor.kind === "status"
      ? `Trạng thái ${editor.name}`
      : editor.kind === "seat"
        ? "Thêm ghế"
        : `${value ? "Sửa" : "Thêm"} ${editor.kind === "cinema" ? "rạp" : editor.kind === "screen" ? "phòng" : "loại ghế"}`;
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (key: string) => String(form.get(key) ?? "").trim();
    let command: CinemaCommand;
    if (editor.kind === "status") {
      const status = text("status") as FacilityStatus;
      command =
        editor.target === "cinema"
          ? {
              kind: "cinema-status",
              id: editor.id,
              status: status as Cinema["status"],
            }
          : {
              kind:
                editor.target === "screen" ? "screen-status" : "seat-status",
              id: editor.id,
              status,
            };
    } else if (editor.kind === "seat")
      command = {
        kind: "seat",
        screenId: editor.screenId,
        input: {
          rowCode: text("rowCode"),
          seatNumber: Number(form.get("seatNumber")),
          seatTypeId: Number(form.get("seatTypeId")),
        },
      };
    else {
      const input = { code: text("code"), name: text("name") };
      command =
        editor.kind === "cinema"
          ? {
              kind: "cinema",
              id: value?.id,
              input: { ...input, location: text("location") },
            }
          : editor.kind === "screen"
            ? {
                kind: "screen",
                id: value?.id,
                cinemaId: editor.cinemaId,
                input,
              }
            : { kind: "seat-type", id: value?.id, input };
    }
    try {
      await mutation.mutateAsync(command);
      close();
    } catch {
      /* The dialog retains the input and displays the API error. */
    }
  }
  return (
    <Dialog title={title} onClose={close} busy={mutation.isPending}>
      <form onSubmit={submit}>
        <fieldset disabled={mutation.isPending}>
          {editor.kind === "status" ? (
            <>
              <p className="notice">
                Thay đổi trạng thái ảnh hưởng khả năng mở bán và chọn ghế. Thao
                tác này không tự hủy lịch chiếu hoặc hoàn tiền vé đã bán.
              </p>
              <Field label="Trạng thái mới">
                <select name="status" defaultValue={editor.status}>
                  {Object.entries(statusNames)
                    .filter(
                      ([s]) =>
                        editor.target !== "cinema" || s !== "MAINTENANCE",
                    )
                    .map(([s, name]) => (
                      <option key={s} value={s}>
                        {name}
                      </option>
                    ))}
                </select>
              </Field>
            </>
          ) : editor.kind === "seat" ? (
            <>
              <Field label="Hàng ghế">
                <input
                  name="rowCode"
                  pattern="[A-Z0-9]{1,8}"
                  maxLength={8}
                  placeholder="A"
                  required
                />
              </Field>
              <Field label="Số ghế">
                <input
                  name="seatNumber"
                  type="number"
                  min={1}
                  step={1}
                  required
                />
              </Field>
              <Field label="Loại ghế">
                <select name="seatTypeId" required defaultValue="">
                  <option value="" disabled>
                    Chọn loại ghế
                  </option>
                  {types.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </Field>
            </>
          ) : (
            <>
              <Field
                label="Mã"
                hint="Chữ hoa, số, dấu gạch dưới hoặc gạch ngang."
              >
                <input
                  name="code"
                  pattern="[A-Z0-9_\-]{1,32}"
                  maxLength={32}
                  defaultValue={value?.code}
                  required
                />
              </Field>
              <Field label="Tên">
                <input
                  name="name"
                  maxLength={editor.kind === "cinema" ? 150 : 100}
                  defaultValue={value?.name}
                  required
                />
              </Field>
              {editor.kind === "cinema" && (
                <Field label="Địa điểm">
                  <input
                    name="location"
                    maxLength={255}
                    defaultValue={editor.value?.location}
                    required
                  />
                </Field>
              )}
            </>
          )}
        </fieldset>
        <ErrorMessage error={mutation.error} />
        <div className="dialog-actions">
          <Button onClick={close} disabled={mutation.isPending}>
            Đóng
          </Button>
          <Button variant="primary" type="submit" pending={mutation.isPending}>
            Lưu
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
