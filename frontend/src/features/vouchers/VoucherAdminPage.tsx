import { useState } from "react";
import { Link } from "react-router-dom";
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
import { useSeatTypes } from "../cinema/hooks";
import { useCombos } from "../combos/hooks";
import { localDate, addDays, formatMoney } from "../../lib/date";
import { kinds, type Voucher, type VoucherInput } from "./api";
import { useAdminVouchers, useVoucherAction } from "./hooks";

export function VoucherAdminPage() {
  const { mode } = useSession();
  return mode === "demo" ? (
    <main className="page">
      <Placeholder
        title="Quản lý voucher"
        description="Mở chế độ API để quản lý ưu đãi."
      />
    </main>
  ) : (
    <Management />
  );
}
function Management() {
  const [page, setPage] = useState(0);
  const query = useAdminVouchers(page);
  const mutation = useVoucherAction();
  const [edit, setEdit] = useState<Voucher | "new" | null>(null);
  const [action, setAction] = useState<{
    voucher: Voucher;
    kind: "assign" | "status";
  }>();
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Quản lý voucher</h1>
          <p>
            Mã đổi vé, combo và giảm giá. Voucher giá trị do hệ thống phát khi
            xử lý hoàn tiền.
          </p>
        </div>
        <Button variant="primary" onClick={() => setEdit("new")}>
          Tạo voucher
        </Button>
      </div>
      <Link className="text-link" to="/admin/promotions">
        Danh mục combo →
      </Link>
      <ErrorMessage
        error={query.error}
        retry={() => {
          void query.refetch();
        }}
      />
      {query.isPending ? (
        <Loading />
      ) : (
        <div className="catalogue-records">
          {query.data?.data.map((v) => (
            <article className="catalogue-record" key={v.id}>
              <div>
                <h2>{v.code}</h2>
                <p>
                  {kinds[v.kind]} ·{" "}
                  {v.discountType === "PERCENT"
                    ? `${v.discountValue}%`
                    : formatMoney(v.discountValue)}
                </p>
                <p className="muted">
                  {v.startDate} – {v.endDate} ·{" "}
                  {v.status === "ACTIVE" ? "Đang kích hoạt" : "Đã tắt"}
                </p>
                <p>
                  {v.ownerId ? `Khách #${v.ownerId}` : "Chưa gán khách"}
                  {v.claimStatus ? ` · ${v.claimStatus}` : ""}
                </p>
                {v.sourceBookingId && (
                  <Link to={`/bookings/${v.sourceBookingId}`}>
                    Đơn phát sinh #{v.sourceBookingId}
                  </Link>
                )}
              </div>
              <div className="record-actions">
                {v.kind !== "VALUE" && !v.ownerId && (
                  <Button onClick={() => setEdit(v)}>Sửa {v.code}</Button>
                )}
                {!v.ownerId && v.kind !== "VALUE" && (
                  <Button
                    onClick={() => {
                      mutation.reset();
                      setAction({ voucher: v, kind: "assign" });
                    }}
                  >
                    Gán khách
                  </Button>
                )}
                {v.kind !== "VALUE" && (
                  <Button
                    onClick={() => {
                      mutation.reset();
                      setAction({ voucher: v, kind: "status" });
                    }}
                  >
                    {v.status === "ACTIVE" ? "Tắt mã" : "Kích hoạt"}
                  </Button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
      {query.data?.data.length === 0 && <EmptyState title="Chưa có voucher" />}
      <Pagination
        page={page}
        totalPages={query.data?.totalPages ?? 0}
        change={setPage}
        busy={query.isFetching}
      />
      {edit && (
        <Editor
          voucher={edit === "new" ? undefined : edit}
          close={() => setEdit(null)}
        />
      )}
      {action && (
        <Dialog
          title={
            action.kind === "assign"
              ? "Gán voucher cho khách"
              : "Đổi trạng thái voucher?"
          }
          busy={mutation.isPending}
          onClose={() => setAction(undefined)}
        >
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const data = new FormData(e.currentTarget);
              try {
                await mutation.mutateAsync(
                  action.kind === "assign"
                    ? {
                        kind: "assign",
                        id: action.voucher.id,
                        userId: Number(data.get("userId")),
                      }
                    : {
                        kind: "status",
                        id: action.voucher.id,
                        active: action.voucher.status !== "ACTIVE",
                      },
                );
                setAction(undefined);
              } catch {
                /* Keep confirmation and errors. */
              }
            }}
          >
            <p className="dialog-description">
              Mã {action.voucher.code}.{" "}
              {action.kind === "assign"
                ? "Mã chỉ thuộc về một khách hàng."
                : "Thay đổi hiệu lực sử dụng mã này."}
            </p>
            {action.kind === "assign" && (
              <Field label="ID khách hàng">
                <input
                  type="number"
                  name="userId"
                  min={1}
                  max={2147483647}
                  step={1}
                  required
                  disabled={mutation.isPending}
                />
              </Field>
            )}
            <ErrorMessage error={mutation.error} />
            <div className="dialog-actions">
              <Button
                disabled={mutation.isPending}
                onClick={() => setAction(undefined)}
              >
                Quay lại
              </Button>
              <Button
                type="submit"
                variant="primary"
                pending={mutation.isPending}
              >
                Xác nhận
              </Button>
            </div>
          </form>
        </Dialog>
      )}
    </main>
  );
}
function Editor({ voucher, close }: { voucher?: Voucher; close: () => void }) {
  const mutation = useVoucherAction();
  const types = useSeatTypes();
  const combos = useCombos(true);
  const [kind, setKind] = useState<VoucherInput["kind"]>(
    voucher?.kind === "VALUE"
      ? "TICKET_DISCOUNT"
      : (voucher?.kind ?? "TICKET_DISCOUNT"),
  );
  const [discountType, setDiscountType] = useState(
    voucher?.discountType ?? "PERCENT",
  );
  const [start, setStart] = useState(voucher?.startDate ?? localDate());
  const exchange = kind.endsWith("EXCHANGE");
  return (
    <Dialog
      title={voucher ? `Sửa ${voucher.code}` : "Tạo voucher"}
      busy={mutation.isPending}
      onClose={close}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          const nullableId = (key: string) => Number(f.get(key)) || null;
          try {
            await mutation.mutateAsync({
              kind: "save",
              id: voucher?.id,
              input: {
                code: voucher?.code ?? String(f.get("code") ?? "").trim(),
                kind,
                discountType: exchange ? "PERCENT" : discountType,
                discountValue: exchange ? 100 : Number(f.get("value")),
                startDate: start,
                endDate: String(f.get("end")),
                minOrderAmount:
                  exchange || !f.get("minimum")
                    ? null
                    : Number(f.get("minimum")),
                seatTypeId: kind.startsWith("TICKET")
                  ? nullableId("seatType")
                  : null,
                comboId: kind === "COMBO_EXCHANGE" ? nullableId("combo") : null,
              },
            });
            close();
          } catch {
            /* Preserve draft. */
          }
        }}
      >
        <fieldset disabled={mutation.isPending}>
          {!voucher && (
            <Field label="Mã (bỏ trống để tự tạo)">
              <input
                name="code"
                maxLength={50}
                minLength={4}
                pattern="[A-Za-z0-9-]+"
              />
            </Field>
          )}
          <Field label="Loại ưu đãi">
            <select
              value={kind}
              onChange={(e) => setKind(e.target.value as VoucherInput["kind"])}
            >
              {Object.entries(kinds)
                .filter(([k]) => k !== "VALUE")
                .map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
            </select>
          </Field>
          {!exchange && (
            <>
              <Field label="Cách giảm">
                <select
                  value={discountType}
                  onChange={(e) =>
                    setDiscountType(e.target.value as "PERCENT" | "FIXED")
                  }
                >
                  <option value="PERCENT">Phần trăm</option>
                  <option value="FIXED">Số tiền VND</option>
                </select>
              </Field>
              <Field
                label={
                  discountType === "PERCENT" ? "Mức giảm (%)" : "Mức giảm (VND)"
                }
              >
                <input
                  name="value"
                  type="number"
                  min={1}
                  max={discountType === "PERCENT" ? 100 : 99999999}
                  step={1}
                  required
                  defaultValue={voucher?.discountValue ?? 10}
                />
              </Field>
              <Field label="Giá trị tối thiểu (VND)">
                <input
                  name="minimum"
                  type="number"
                  min={0}
                  max={99999999}
                  step={1}
                  defaultValue={voucher?.minOrderAmount ?? ""}
                />
              </Field>
            </>
          )}
          {exchange && (
            <p className="notice">
              Mã đổi thanh toán 100% giá trị của một vé hoặc một phần combo.
            </p>
          )}
          <div className="form-grid">
            <Field label="Từ ngày">
              <input
                type="date"
                value={start}
                onChange={(e) => setStart(e.target.value)}
                required
              />
            </Field>
            <Field label="Đến ngày">
              <input
                name="end"
                type="date"
                min={start}
                defaultValue={voucher?.endDate ?? addDays(localDate(), 30)}
                required
              />
            </Field>
          </div>
          {kind.startsWith("TICKET") && (
            <Field label="Loại ghế áp dụng">
              <select name="seatType" defaultValue={voucher?.seatTypeId ?? ""}>
                <option value="">Tất cả loại ghế</option>
                {types.data?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
                {voucher?.seatTypeId &&
                  !types.data?.some((t) => t.id === voucher.seatTypeId) && (
                    <option value={voucher.seatTypeId}>
                      Loại ghế #{voucher.seatTypeId}
                    </option>
                  )}
              </select>
            </Field>
          )}
          {kind === "COMBO_EXCHANGE" && (
            <Field label="Combo được đổi">
              <select
                name="combo"
                required
                defaultValue={voucher?.comboId ?? ""}
              >
                <option value="">Chọn combo</option>
                {combos.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
                {voucher?.comboId &&
                  !combos.data?.some((c) => c.id === voucher.comboId) && (
                    <option value={voucher.comboId}>
                      Combo #{voucher.comboId}
                    </option>
                  )}
              </select>
            </Field>
          )}
        </fieldset>
        <ErrorMessage error={types.error || combos.error} />
        <ErrorMessage error={mutation.error} />
        <div className="dialog-actions">
          <Button disabled={mutation.isPending} onClick={close}>
            Đóng
          </Button>
          <Button type="submit" variant="primary" pending={mutation.isPending}>
            Lưu voucher
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
