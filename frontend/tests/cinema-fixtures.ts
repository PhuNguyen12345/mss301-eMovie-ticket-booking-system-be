export const cinemas = [
  {
    id: 1,
    code: "HN",
    name: "Rạp Hà Nội",
    location: "Hà Nội",
    status: "ACTIVE",
  },
  {
    id: 2,
    code: "HCM",
    name: "Rạp Hồ Chí Minh",
    location: "Hồ Chí Minh",
    status: "ACTIVE",
  },
];
export const screens = [
  { id: 1, cinemaId: 1, code: "S1", name: "Phòng một", status: "ACTIVE" },
  { id: 2, cinemaId: 2, code: "S2", name: "Phòng hai", status: "ACTIVE" },
];
export const seatTypes = [
  { id: 1, code: "STANDARD", name: "Standard" },
  { id: 2, code: "VIP", name: "VIP" },
];
export function cinemaResponse(path: string): unknown {
  if (path === "/api/cinemas") return cinemas;
  if (path === "/api/seat-types") return seatTypes;
  const list = path.match(/^\/api\/cinemas\/(\d+)\/screens$/);
  if (list) return screens.filter((s) => s.cinemaId === Number(list[1]));
  const cinema = path.match(/^\/api\/cinemas\/(\d+)$/);
  if (cinema) return cinemas.find((c) => c.id === Number(cinema[1]));
  const screen = path.match(/^\/api\/screens\/(\d+)$/);
  if (screen) return screens.find((s) => s.id === Number(screen[1]));
  if (/^\/api\/screens\/\d+\/seats$/.test(path)) return [];
  return undefined;
}
