export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function addDays(date: string, count: number) {
  const value = new Date(`${date}T12:00:00`);
  value.setDate(value.getDate() + count);
  return localDate(value);
}
export const formatTime = (date: string) => date.slice(11, 16);
export const formatDate = (date: string) =>
  new Date(`${date.slice(0, 10)}T12:00:00`).toLocaleDateString("vi-VN");
export const formatMoney = (amount: number) =>
  `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(amount)} đ`;
export const hasStarted = (date: string) =>
  new Date(`${date.replace(/(?:Z|[+-]\d{2}:\d{2})$/, "")}+07:00`).getTime() <=
  Date.now();
