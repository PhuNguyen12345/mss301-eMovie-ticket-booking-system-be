import { useEffect, useState } from "react";
import { serverTimestamp } from "./date";
export function useCountdown(deadline?: string) {
  const [now, setNow] = useState(Date.now);
  const end = deadline ? serverTimestamp(deadline) : 0;
  useEffect(() => {
    if (!deadline) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [deadline]);
  const seconds = Math.max(0, Math.ceil((end - now) / 1000));
  return {
    expired: seconds <= 0,
    label: `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`,
  };
}
