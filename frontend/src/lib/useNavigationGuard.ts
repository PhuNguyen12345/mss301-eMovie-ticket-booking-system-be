import { useCallback, useEffect, useRef } from "react";
import { useBlocker } from "react-router-dom";
export function useNavigationGuard(
  active: boolean,
  message = "Bỏ thay đổi chưa lưu?",
) {
  const latest = useRef(active);
  latest.current = active;
  const blocker = useBlocker(useCallback(() => latest.current, []));
  const handled = useRef(false);
  useEffect(() => {
    if (blocker.state !== "blocked") {
      handled.current = false;
      return;
    }
    if (handled.current) return;
    handled.current = true;
    if (window.confirm(message)) blocker.proceed();
    else blocker.reset();
  }, [blocker, message]);
  useEffect(() => {
    if (!active) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [active]);
}
