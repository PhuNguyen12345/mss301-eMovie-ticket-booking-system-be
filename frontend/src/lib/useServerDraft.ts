import { useEffect, useState } from "react";

// Background refetches may update the baseline only when the user has no edits.
export function useServerDraft<T>(remote: T) {
  const [baseline, setBaseline] = useState(remote);
  const [value, setValue] = useState(remote);
  const dirty = JSON.stringify(value) !== JSON.stringify(baseline);
  const remoteChanged = JSON.stringify(remote) !== JSON.stringify(baseline);
  useEffect(() => {
    if (!dirty && remoteChanged) {
      setBaseline(remote);
      setValue(remote);
    }
  }, [dirty, remoteChanged, remote]);
  return {
    value,
    setValue,
    dirty,
    remoteChanged,
    reset: (next: T = remote) => {
      setBaseline(next);
      setValue(next);
    },
  };
}
