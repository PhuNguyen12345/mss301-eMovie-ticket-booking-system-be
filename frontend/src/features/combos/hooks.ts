import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { ComboInput } from "./api";
export function useCombos(all = false) {
  const { combos } = useServices();
  const { current, isAdmin, mode } = useSession();
  return useQuery({
    queryKey: all ? ["private", current?.user.userId, "combos"] : ["combos"],
    queryFn: ({ signal }) => combos.list(all, signal),
    enabled: mode === "api" && (!all || isAdmin),
  });
}
export function useManageCombo() {
  const { combos } = useServices();
  const cache = useQueryClient();
  const { current } = useSession();
  return useMutation({
    mutationFn: (
      command:
        { input: ComboInput; id?: number } | { id: number; active: boolean },
    ) =>
      "active" in command
        ? combos.status(command.id, command.active)
        : combos.save(command.input, command.id),
    onSuccess: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: ["combos"] }),
        cache.invalidateQueries({
          queryKey: ["private", current?.user.userId, "combos"],
        }),
      ]);
    },
  });
}
