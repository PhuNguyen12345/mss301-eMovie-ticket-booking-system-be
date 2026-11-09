import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import { Loading, ErrorMessage } from "../../components/ui/Feedback";

export function TicketQr({ bookingId }: { bookingId: number }) {
  const { booking } = useServices();
  const { current, mode } = useSession();
  const [image, setImage] = useState<{ blob: Blob; url: string }>();
  const query = useQuery({
    queryKey: ["private", current?.user.userId, "ticket-qr", bookingId],
    queryFn: ({ signal }) => booking.qr(bookingId, signal),
    enabled: mode === "api" && !!current,
    gcTime: 0,
    staleTime: 0,
    retry: false,
  });
  useEffect(() => {
    if (!query.data) return;
    const url = URL.createObjectURL(query.data);
    setImage({ blob: query.data, url });
    return () => URL.revokeObjectURL(url);
  }, [query.data]);
  return (
    <section className="ticket-qr">
      <h3>Mã QR vào rạp</h3>
      {query.isPending ? (
        <Loading label="Đang tải mã QR…" />
      ) : query.isError ? (
        <ErrorMessage
          error={query.error}
          retry={() => {
            void query.refetch();
          }}
        />
      ) : (
        image &&
        image.blob === query.data && (
          <img
            src={image.url}
            width={300}
            height={300}
            alt="Mã QR vé do hệ thống cấp"
          />
        )
      )}
      <p className="muted">
        Xuất trình mã này tại cửa vào. Nhân viên xác nhận số khách bằng hệ thống
        soát vé.
      </p>
    </section>
  );
}
