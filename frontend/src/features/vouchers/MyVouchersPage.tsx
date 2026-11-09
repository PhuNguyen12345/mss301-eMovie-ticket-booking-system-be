import { useState } from "react";
import { AccessGate } from "../../components/layout/AccessGate";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import {
  Loading,
  ErrorMessage,
  EmptyState,
} from "../../components/ui/Feedback";
import { formatMoney } from "../../lib/date";
import { availability, kinds, type MyVoucher } from "./api";
import { useVouchers, useVoucherAction } from "./hooks";

export function VoucherTerms({ voucher }: { voucher: MyVoucher }) {
  return (
    <>
      <p>
        {kinds[voucher.kind]} ·{" "}
        {voucher.discountType === "PERCENT"
          ? `${voucher.discountValue}%`
          : formatMoney(voucher.discountValue)}
      </p>
      <p className="muted">
        Hiệu lực: {voucher.startDate} – {voucher.endDate}
      </p>
      {voucher.minOrderAmount !== null && (
        <p className="muted">
          Giá trị tối thiểu của phần được giảm:{" "}
          {formatMoney(voucher.minOrderAmount)}
        </p>
      )}
      {voucher.seatTypeId && (
        <p className="muted">Loại ghế #{voucher.seatTypeId}</p>
      )}
      {voucher.comboId && <p className="muted">Combo #{voucher.comboId}</p>}
    </>
  );
}
export function MyVouchersPage() {
  return (
    <AccessGate>
      <Wallet />
    </AccessGate>
  );
}
function Wallet() {
  const query = useVouchers();
  const mutation = useVoucherAction();
  const [code, setCode] = useState("");
  const [claimed, setClaimed] = useState<string>();
  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <h1>Ưu đãi của tôi</h1>
          <p>
            Nhận mã và áp dụng tại bước thanh toán. Voucher hoàn tiền cũng xuất
            hiện ở đây.
          </p>
        </div>
      </div>
      <form
        className="surface order-filters"
        onSubmit={async (e) => {
          e.preventDefault();
          setClaimed(undefined);
          try {
            const result = await mutation.mutateAsync({
              kind: "claim",
              code: code.trim(),
            });
            setClaimed(result.code);
            setCode("");
          } catch {
            /* Keep code for retry. */
          }
        }}
      >
        <Field label="Mã ưu đãi">
          <input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            required
            maxLength={50}
            autoComplete="off"
          />
        </Field>
        <Button type="submit" variant="primary" pending={mutation.isPending}>
          Nhận mã
        </Button>
      </form>
      <ErrorMessage error={mutation.error} />
      {claimed && (
        <p role="status" className="notice">
          Đã nhận mã {claimed}.
        </p>
      )}
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
          {query.data?.map((v) => (
            <article className="catalogue-record" key={v.id}>
              <div>
                <h2>{v.code}</h2>
                <VoucherTerms voucher={v} />
              </div>
              <span className="badge">{availability[v.status]}</span>
            </article>
          ))}
        </div>
      )}
      {query.data?.length === 0 && (
        <EmptyState
          title="Chưa có voucher"
          description="Nhập mã được tặng để nhận ưu đãi."
        />
      )}
    </main>
  );
}
