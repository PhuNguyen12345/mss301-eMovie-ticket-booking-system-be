import { Link } from "react-router-dom";
import { useState } from "react";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Field } from "../../components/ui/Field";
import {
  EmptyState,
  ErrorMessage,
  Loading,
} from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { useSession } from "../../app/ServicesProvider";
import { formatMoney } from "../../lib/date";
import { useCombos, useManageCombo } from "./hooks";
import type { Combo } from "./api";
export function ComboAdminPage() {
  const { mode } = useSession();
  if (mode === "demo")
    return (
      <main className="page">
        <Placeholder
          title="Quản lý combo"
          description="Mở chế độ API để quản lý combo."
        />
      </main>
    );
  return <ComboManagement />;
}
function ComboManagement() {
  const query = useCombos(true);
  const status = useManageCombo();
  const [edit, setEdit] = useState<Combo | "create" | null>(null);
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Combo & ưu đãi</h1>
          <p>Danh mục bắp, nước và combo bán kèm vé.</p>
        </div>
        <Button variant="primary" onClick={() => setEdit("create")}>
          Thêm combo
        </Button>
      </div>
      <ErrorMessage
        error={query.error}
        retry={() => {
          void query.refetch();
        }}
      />
      <ErrorMessage error={status.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        <div className="catalogue-records">
          {query.data?.map((combo) => (
            <article className="catalogue-record" key={combo.id}>
              <div>
                <h2>{combo.name}</h2>
                <p>{combo.description}</p>
                <strong>{formatMoney(combo.price)}</strong>
                <span className="badge">
                  {combo.status === "ACTIVE" ? "Đang bán" : "Ngừng bán"}
                </span>
              </div>
              <div className="record-actions">
                <Button onClick={() => setEdit(combo)}>Sửa {combo.name}</Button>
                <Button
                  disabled={status.isPending}
                  onClick={() =>
                    status.mutate({
                      id: combo.id,
                      active: combo.status !== "ACTIVE",
                    })
                  }
                >
                  {combo.status === "ACTIVE" ? "Ngừng bán" : "Mở bán"}
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}
      {query.data?.length === 0 && <EmptyState title="Chưa có combo" />}
      <Link className="text-link" to="/admin/vouchers">
        Quản lý mã ưu đãi →
      </Link>
      {edit && (
        <ComboDialog
          combo={edit === "create" ? undefined : edit}
          close={() => setEdit(null)}
        />
      )}
    </main>
  );
}
function ComboDialog({ combo, close }: { combo?: Combo; close: () => void }) {
  const mutation = useManageCombo();
  return (
    <Dialog
      title={combo ? "Sửa combo" : "Thêm combo"}
      onClose={close}
      busy={mutation.isPending}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          try {
            await mutation.mutateAsync({
              id: combo?.id,
              input: {
                name: String(data.get("name")).trim(),
                description: String(data.get("description")).trim() || null,
                price: Number(data.get("price")),
              },
            });
            close();
          } catch {
            /* Keep the draft after a failed write. */
          }
        }}
      >
        <fieldset disabled={mutation.isPending}>
          <Field label="Tên combo">
            <input
              name="name"
              maxLength={100}
              defaultValue={combo?.name}
              required
            />
          </Field>
          <Field label="Mô tả">
            <textarea
              name="description"
              maxLength={255}
              defaultValue={combo?.description ?? ""}
            />
          </Field>
          <Field label="Giá (VND)">
            <input
              name="price"
              type="number"
              min="0.01"
              step="0.01"
              max="99999999.99"
              defaultValue={combo?.price}
              required
            />
          </Field>
        </fieldset>
        <ErrorMessage error={mutation.error} />
        <div className="dialog-actions">
          <Button disabled={mutation.isPending} onClick={close}>
            Đóng
          </Button>
          <Button type="submit" variant="primary" pending={mutation.isPending}>
            Lưu combo
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
