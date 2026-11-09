import { z } from "zod";
import type { HttpClient } from "../../lib/http";
export const comboSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  description: z.string().nullable(),
  price: z.number().positive(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
});
export type Combo = z.infer<typeof comboSchema>;
export type ComboInput = Pick<Combo, "name" | "description" | "price">;
export function createComboApi(client: HttpClient) {
  return {
    list: (all: boolean, signal?: AbortSignal) =>
      client.request(all ? "/combos/all" : "/combos", z.array(comboSchema), {
        signal,
      }),
    save: (input: ComboInput, id?: number) =>
      client.request(id ? `/combos/${id}` : "/combos", comboSchema, {
        method: id ? "PUT" : "POST",
        body: input,
      }),
    status: (id: number, active: boolean) =>
      client.request(
        `/combos/${id}/${active ? "activate" : "deactivate"}`,
        comboSchema,
        { method: "POST" },
      ),
  };
}
