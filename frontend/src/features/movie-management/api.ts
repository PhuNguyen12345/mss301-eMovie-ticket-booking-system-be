import { z } from "zod";
import { ApiError, type HttpClient } from "../../lib/http";
import { pageSchema, type Page } from "../showtimes/model/contracts";

const id = z.number().int().positive().safe();
export const genreSchema = z.object({
  id,
  name: z.string(),
  description: z.string().nullish(),
});
export const personSchema = z.object({ id, fullName: z.string() });
export const creditSchema = z.object({
  id,
  personId: id,
  fullName: z.string(),
  creditType: z.enum(["ACTOR", "DIRECTOR"]),
  characterName: z.string().nullable(),
  displayOrder: z.number().int().positive(),
});
const posterSchema = z.object({ movieId: id, posterUrl: z.string().url() });
export type ReferenceKind = "genres" | "persons";
export type Reference = {
  id: number;
  name: string;
  description?: string | null;
};
export type ReferenceSearch = { keyword: string; page: number; size: number };
export type Credit = z.infer<typeof creditSchema>;
export type CreditInput = Omit<Credit, "id" | "fullName">;
export type ReferenceInput = Omit<Reference, "id">;

export function validatePoster(file: File) {
  if (!file.size) throw new Error("Vui lòng chọn ảnh poster.");
  if (file.size > 5 * 1024 * 1024)
    throw new Error("Poster không được vượt quá 5 MB.");
  if (!["image/jpeg", "image/png"].includes(file.type))
    throw new Error("Chọn ảnh JPG hoặc PNG.");
}
export function createMovieManagementApi(client: HttpClient) {
  return {
    async references(
      kind: ReferenceKind,
      filters: ReferenceSearch,
      signal?: AbortSignal,
    ): Promise<Page<Reference>> {
      const params = new URLSearchParams({
        keyword: filters.keyword,
        page: String(filters.page),
        size: String(filters.size),
      });
      const path = `/${kind}?${params}`;
      if (kind === "genres")
        return client.request(path, pageSchema(genreSchema), { signal });
      const result = await client.request(path, pageSchema(personSchema), {
        signal,
      });
      return {
        ...result,
        data: result.data.map((person) => ({
          id: person.id,
          name: person.fullName,
        })),
      };
    },
    async saveReference(
      kind: ReferenceKind,
      input: ReferenceInput,
      id?: number,
    ): Promise<Reference> {
      const path = id ? `/${kind}/${id}` : `/${kind}`;
      const method = id ? "PUT" : "POST";
      if (kind === "genres")
        return client.request(path, genreSchema, { method, body: input });
      const person = await client.request(path, personSchema, {
        method,
        body: { fullName: input.name },
      });
      return { id: person.id, name: person.fullName };
    },
    deleteReference: (kind: ReferenceKind, id: number) =>
      client.request(`/${kind}/${id}`, z.null(), { method: "DELETE" }),
    genres: (id: number, signal?: AbortSignal) =>
      client.request(`/movies/${id}/genres`, z.array(genreSchema), { signal }),
    credits: (id: number, signal?: AbortSignal) =>
      client.request(`/movies/${id}/credits`, z.array(creditSchema), {
        signal,
      }),
    replaceGenres: (id: number, genreIds: number[]) =>
      client.request(`/movies/${id}/genres`, z.array(genreSchema), {
        method: "PUT",
        body: { genreIds },
      }),
    replaceCredits: (id: number, credits: CreditInput[]) =>
      client.request(`/movies/${id}/credits`, z.array(creditSchema), {
        method: "PUT",
        body: { credits },
      }),
    uploadPoster: (id: number, file: File) => {
      validatePoster(file);
      const body = new FormData();
      body.append("file", file);
      return client.request(`/movies/${id}/poster`, posterSchema, {
        method: "PUT",
        body,
        timeoutMs: 60000,
      });
    },
    importPoster: (id: number, url: string) => {
      const parsed = z.url({ protocol: /^https?$/ }).safeParse(url);
      if (!parsed.success)
        throw new ApiError(400, "Đường dẫn ảnh phải dùng HTTP hoặc HTTPS.");
      return client.request(`/movies/${id}/poster/from-url`, posterSchema, {
        method: "PUT",
        body: { url },
        timeoutMs: 60000,
      });
    },
  };
}
