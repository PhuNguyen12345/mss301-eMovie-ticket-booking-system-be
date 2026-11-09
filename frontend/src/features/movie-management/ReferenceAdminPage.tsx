import { useState } from "react";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Field } from "../../components/ui/Field";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { Pagination } from "../../components/ui/Pagination";
import { Placeholder } from "../../components/ui/Placeholder";
import { MovieAdminNav } from "./MovieAdminNav";
import { useReferences, useReferenceMutation } from "./hooks";
import type { Reference, ReferenceKind } from "./api";

export function GenresPage() {
  return <ReferenceAdminPage kind="genres" />;
}
export function PersonsPage() {
  return <ReferenceAdminPage kind="persons" />;
}
function ReferenceAdminPage({ kind }: { kind: ReferenceKind }) {
  const { mode } = useSession();
  const label = kind === "genres" ? "Thể loại" : "Diễn viên & đạo diễn";
  if (mode === "demo")
    return (
      <main className="page">
        <MovieAdminNav />
        <Placeholder
          title={label}
          description="Mở chế độ API để quản lý danh mục."
        />
      </main>
    );
  return <ReferenceList key={kind} kind={kind} label={label} />;
}
function ReferenceList({
  kind,
  label,
}: {
  kind: ReferenceKind;
  label: string;
}) {
  const [filters, setFilters] = useState({ keyword: "", page: 0, size: 10 });
  const query = useReferences(kind, filters);
  const [edit, setEdit] = useState<Reference | "create" | null>(null);
  const [deleting, setDeleting] = useState<Reference | null>(null);
  const mutation = useReferenceMutation(kind);
  return (
    <main className="page">
      <MovieAdminNav />
      <div className="page-heading">
        <div>
          <h1>{label}</h1>
          <p>Danh mục dùng chung cho các phim.</p>
        </div>
        <Button variant="primary" onClick={() => setEdit("create")}>
          {kind === "genres" ? "Thêm thể loại" : "Thêm người"}
        </Button>
      </div>
      <form
        className="admin-filters"
        onSubmit={(e) => {
          e.preventDefault();
          setFilters({
            ...filters,
            keyword: String(
              new FormData(e.currentTarget).get("keyword"),
            ).trim(),
            page: 0,
          });
        }}
      >
        <Field label="Tìm theo tên">
          <input name="keyword" type="search" maxLength={255} />
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
          <div className="catalogue-records">
            {query.data.data.map((row) => (
              <article className="catalogue-record" key={row.id}>
                <div>
                  <h2>{row.name}</h2>
                  {row.description && (
                    <p className="muted">{row.description}</p>
                  )}
                </div>
                <div className="record-actions">
                  <Button
                    aria-label={`Sửa ${row.name}`}
                    onClick={() => setEdit(row)}
                  >
                    Sửa
                  </Button>
                  <Button
                    variant="danger"
                    aria-label={`Xóa ${row.name}`}
                    onClick={() => {
                      mutation.reset();
                      setDeleting(row);
                    }}
                  >
                    Xóa
                  </Button>
                </div>
              </article>
            ))}
          </div>
          {!query.data.data.length && (
            <EmptyState title="Không có kết quả phù hợp" />
          )}
          <Pagination
            page={filters.page}
            totalPages={query.data.totalPages}
            change={(page) => setFilters((previous) => ({ ...previous, page }))}
          />
        </>
      )}
      {edit && (
        <ReferenceDialog
          kind={kind}
          value={edit === "create" ? undefined : edit}
          close={() => setEdit(null)}
        />
      )}
      {deleting && (
        <Dialog
          title={`Xóa ${deleting.name}?`}
          onClose={() => setDeleting(null)}
          busy={mutation.isPending}
        >
          <p className="dialog-description">
            Thao tác xóa khỏi danh mục. Mục đang được phim sử dụng có thể bị hệ
            thống từ chối xóa.
          </p>
          <ErrorMessage error={mutation.error} />
          <div className="dialog-actions">
            <Button
              disabled={mutation.isPending}
              onClick={() => setDeleting(null)}
            >
              Giữ lại
            </Button>
            <Button
              variant="danger"
              pending={mutation.isPending}
              onClick={async () => {
                try {
                  await mutation.mutateAsync({ deleteId: deleting.id });
                  setDeleting(null);
                } catch {
                  /* Keep the server conflict visible. */
                }
              }}
            >
              Xác nhận xóa
            </Button>
          </div>
        </Dialog>
      )}
    </main>
  );
}
function ReferenceDialog({
  kind,
  value,
  close,
}: {
  kind: ReferenceKind;
  value?: Reference;
  close: () => void;
}) {
  const mutation = useReferenceMutation(kind);
  return (
    <Dialog
      title={`${value ? "Sửa" : "Thêm"} ${kind === "genres" ? "thể loại" : "diễn viên / đạo diễn"}`}
      onClose={close}
      busy={mutation.isPending}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const form = new FormData(e.currentTarget);
          try {
            await mutation.mutateAsync({
              id: value?.id,
              input: {
                name: String(form.get("name")).trim(),
                ...(kind === "genres"
                  ? {
                      description:
                        String(form.get("description")).trim() || null,
                    }
                  : {}),
              },
            });
            close();
          } catch {
            /* Retain the draft after a failed write. */
          }
        }}
      >
        <fieldset disabled={mutation.isPending}>
          <Field label={kind === "genres" ? "Tên thể loại" : "Họ và tên"}>
            <input
              name="name"
              maxLength={kind === "genres" ? 100 : 255}
              defaultValue={value?.name}
              required
            />
          </Field>
          {kind === "genres" && (
            <Field label="Mô tả">
              <textarea
                name="description"
                maxLength={500}
                defaultValue={value?.description ?? ""}
                rows={3}
              />
            </Field>
          )}
        </fieldset>
        <ErrorMessage error={mutation.error} />
        <div className="dialog-actions">
          <Button disabled={mutation.isPending} onClick={close}>
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
