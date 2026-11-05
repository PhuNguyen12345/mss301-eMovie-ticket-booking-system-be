export type SeatPrice = { seatTypeId: number; price: number };
export type Showtime = {
  id: number;
  movieId: number;
  cinemaId: number;
  screenId: number;
  startTime: string;
  endTime: string;
  basePrice: number;
  status: "SCHEDULED" | "CANCELLED";
  prices: SeatPrice[];
};
export const movies = [
  {
    id: 1,
    title: "Interstellar",
    subtitle: "Hành trình vượt không gian",
    duration: 169,
    age: "T13",
    genre: "Khoa học viễn tưởng",
    art: "space",
    year: "2014",
  },
  {
    id: 2,
    title: "Dune: Part Two",
    subtitle: "Hành tinh cát: Phần hai",
    duration: 166,
    age: "T16",
    genre: "Phiêu lưu",
    art: "dune",
    year: "2024",
  },
  {
    id: 3,
    title: "Spirited Away",
    subtitle: "Vùng đất linh hồn",
    duration: 125,
    age: "P",
    genre: "Hoạt hình",
    art: "spirit",
    year: "2001",
  },
  {
    id: 4,
    title: "La La Land",
    subtitle: "Những kẻ khờ mộng mơ",
    duration: 128,
    age: "T13",
    genre: "Âm nhạc",
    art: "jazz",
    year: "2016",
  },
];
export const cinemas = [
  { id: 1, name: "eMovie Thảo Điền", location: "Thành phố Hồ Chí Minh" },
  { id: 2, name: "eMovie Tràng Tiền", location: "Hà Nội" },
];
export const seatTypes = [
  { id: 1, name: "Standard", description: "Ghế tiêu chuẩn" },
  { id: 2, name: "VIP", description: "Vị trí đẹp, thêm thoải mái" },
  { id: 3, name: "Couple", description: "Ghế đôi, cùng trải nghiệm" },
];
export const localDate = (date = new Date()) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
export const today = localDate();
export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN").format(value);
export const time = (value: string) => value.slice(11, 16);
export function addDays(date: string, count: number) {
  const d = new Date(`${date}T12:00:00`);
  d.setDate(d.getDate() + count);
  return localDate(d);
}
const key = "emovie-studio-demo-v1";

function seed(): Showtime[] {
  return Array.from({ length: 7 }, (_, day) =>
    [
      { movieId: 1, screenId: 1, hour: "18:00", basePrice: 90000 },
      { movieId: 2, screenId: 2, hour: "18:30", basePrice: 95000 },
      { movieId: 3, screenId: 3, hour: "19:00", basePrice: 85000 },
      { movieId: 4, screenId: 1, hour: "21:15", basePrice: 90000 },
      { movieId: 1, screenId: 2, hour: "21:30", basePrice: 95000 },
    ].map((item, index) => {
      const startTime = `${addDays(today, day)}T${item.hour}:00`;
      const end = new Date(
        new Date(startTime).getTime() +
          movies.find((m) => m.id === item.movieId)!.duration * 60000,
      );
      return {
        ...item,
        id: 101 + day * 10 + index,
        cinemaId: 1,
        startTime,
        endTime: `${localDate(end)}T${String(end.getHours()).padStart(2, "0")}:${String(end.getMinutes()).padStart(2, "0")}:00`,
        status: "SCHEDULED" as const,
        prices:
          index === 2
            ? []
            : [
                { seatTypeId: 1, price: item.basePrice },
                { seatTypeId: 2, price: item.basePrice + 30000 },
                { seatTypeId: 3, price: item.basePrice * 2 },
              ],
      };
    }),
  ).flat();
}
export function loadShows(): Showtime[] {
  try {
    const saved = JSON.parse(localStorage.getItem(key) || "null");
    if (saved?.day === today && Array.isArray(saved.shows)) return saved.shows;
  } catch {
    /* A corrupt demo cache can be safely regenerated. */
  }
  return seed();
}
export function persist(shows: Showtime[]) {
  localStorage.setItem(key, JSON.stringify({ day: today, shows }));
}
export function validatePrices(prices: SeatPrice[]) {
  if (new Set(prices.map((p) => p.seatTypeId)).size !== prices.length)
    throw new Error("Mỗi loại ghế chỉ có một mức giá.");
  for (const item of prices) {
    if (
      !Number.isFinite(item.price) ||
      item.price < 0 ||
      item.price > 9999999999.99 ||
      Math.abs(item.price * 100 - Math.round(item.price * 100)) > 0.001
    ) {
      throw new Error(
        "Giá vé phải từ 0 đến 9.999.999.999,99 đ, tối đa 2 chữ số thập phân.",
      );
    }
  }
}
export function createShow(
  shows: Showtime[],
  input: {
    movieId: number;
    cinemaId: number;
    screenId: number;
    startTime: string;
    basePrice: number;
  },
): Showtime {
  const movie = movies.find((m) => m.id === input.movieId);
  const start = new Date(input.startTime);
  if (!movie || !Number.isFinite(start.getTime()) || start <= new Date())
    throw new Error("Vui lòng chọn phim và giờ chiếu trong tương lai.");
  validatePrices([{ seatTypeId: 1, price: input.basePrice }]);
  const end = new Date(start.getTime() + movie.duration * 60000);
  if (
    shows.some(
      (s) =>
        s.cinemaId === input.cinemaId &&
        s.screenId === input.screenId &&
        s.status === "SCHEDULED" &&
        new Date(s.startTime) < end &&
        new Date(s.endTime) > start,
    )
  ) {
    throw new Error(
      "Phòng này đã có suất chiếu trong khung giờ đã chọn. Vui lòng đổi giờ hoặc phòng.",
    );
  }
  return {
    ...input,
    id: Math.max(100, ...shows.map((s) => s.id)) + 1,
    endTime: `${localDate(end)}T${String(end.getHours()).padStart(2, "0")}:${String(end.getMinutes()).padStart(2, "0")}:00`,
    status: "SCHEDULED",
    prices: [],
  };
}
