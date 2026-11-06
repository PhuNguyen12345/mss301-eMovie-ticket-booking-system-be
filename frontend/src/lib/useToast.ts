import { useCallback, useEffect, useState } from "react";
export function useToast() {
  const [message, setMessage] = useState("");
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(""), 4500);
    return () => clearTimeout(timer);
  }, [message]);
  return {
    message,
    notify: setMessage,
    dismiss: useCallback(() => setMessage(""), []),
  };
}
