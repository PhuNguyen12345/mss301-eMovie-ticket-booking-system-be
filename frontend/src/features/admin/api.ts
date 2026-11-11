import { z } from "zod";
import type { HttpClient } from "../../lib/http";
export const customerSchema = z.object({
  userId: z.number().int().positive().safe(),
  fullName: z.string(),
  email: z.string(),
  phone: z.string().nullable(),
  status: z.string(),
});
export type Customer = z.infer<typeof customerSchema>;
/** At most this many ids per lookup, as user-service enforces. */
export const MAX_CUSTOMER_LOOKUP = 100;
export function customerIds(ids: readonly number[]) {
  return [...new Set(ids)]
    .filter((id) => Number.isSafeInteger(id) && id > 0)
    .sort((a, b) => a - b)
    .slice(0, MAX_CUSTOMER_LOOKUP);
}
export function createAdminApi(client: HttpClient) {
  return {
    customers: (ids: readonly number[], signal?: AbortSignal) =>
      client.request(
        `/users?ids=${customerIds(ids).join(",")}`,
        z.array(customerSchema),
        { signal },
      ),
  };
}
