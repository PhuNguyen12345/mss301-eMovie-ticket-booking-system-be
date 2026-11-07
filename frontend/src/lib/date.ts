export function localDate(date = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}
export function addDays(date: string, count: number) {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + count);
  return value.toISOString().slice(0, 10);
}
export const formatTime = (date: string) => date.slice(11, 16);
export const formatDate = (date: string) =>
  new Date(`${date.slice(0, 10)}T12:00:00`).toLocaleDateString("vi-VN");
export const formatMoney = (amount: number) =>
  `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(amount)} đ`;
export const hasStarted = (date: string) => serverTimestamp(date) <= Date.now();
export const serverTimestamp = (date: string) =>
  new Date(
    /(?:Z|[+-]\d{2}:\d{2})$/.test(date) ? date : `${date}+07:00`,
  ).getTime();
