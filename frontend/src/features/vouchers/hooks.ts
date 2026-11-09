import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useServices, useSession } from "../../app/ServicesProvider";
import type { VoucherInput } from "./api";

export function useVouchers() {
  const { vouchers } = useServices();
  const { current, mode } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "vouchers", "mine"],
    queryFn: ({ signal }) => vouchers.mine(signal),
    enabled: mode === "api" && !!current,
  });
}
export function useAdminVouchers(page: number) {
  const { vouchers } = useServices();
  const { current, isAdmin, mode } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "vouchers", "admin", page],
    queryFn: ({ signal }) => vouchers.list(page, signal),
    enabled: mode === "api" && isAdmin,
  });
}
type Command =
  | { kind: "claim"; code: string }
  | { kind: "save"; input: VoucherInput; id?: number }
  | { kind: "status"; id: number; active: boolean }
  | { kind: "assign"; id: number; userId: number };
export function useVoucherAction() {
  const { vouchers, session } = useServices();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: async (command: Command) => {
      switch (command.kind) {
        case "claim":
          return vouchers.claim(command.code);
        case "save":
          return vouchers.save(command.input, command.id);
        case "status":
          return vouchers.status(command.id, command.active);
        case "assign":
          return vouchers.assign(command.id, command.userId);
      }
    },
    onMutate: () => session.getSnapshot(),
    onSuccess: async (_result, _command, owner) => {
      if (owner && session.getSnapshot()?.sessionId === owner.sessionId)
        await cache.invalidateQueries({
          queryKey: ["private", owner.user.userId, "vouchers"],
        });
    },
  });
}
