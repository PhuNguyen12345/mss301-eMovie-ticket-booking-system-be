import { useCinema, useScreen } from "./hooks";

export function VenueName({
  cinemaId,
  screenId,
}: {
  cinemaId: number;
  screenId: number;
}) {
  const cinema = useCinema(cinemaId);
  const screen = useScreen(screenId);
  return (
    <>
      {cinema.data?.name ?? `Rạp #${cinemaId}`} ·{" "}
      {screen.data?.name ?? `Phòng #${screenId}`}
    </>
  );
}
