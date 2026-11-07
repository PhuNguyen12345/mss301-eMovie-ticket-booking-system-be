import { useMutation, useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { useServices, useSession } from "../../app/ServicesProvider";
const profileSchema = z.object({
  userId: z.number().int().positive(),
  fullName: z.string(),
  email: z.string(),
  phone: z.string().nullable(),
  dateOfBirth: z.string().nullable(),
  gender: z.string().nullable(),
  cityProvince: z.string().nullable(),
  district: z.string().nullable(),
  address: z.string().nullable(),
  preferredCinemaId: z.number().nullable(),
  avatarUrl: z.string().nullable(),
});
export function useProfile() {
  const { client } = useServices();
  const { current } = useSession();
  return useQuery({
    queryKey: ["private", current?.user.userId, "profile"],
    queryFn: ({ signal }) =>
      client.request("/users/me", profileSchema, { signal }),
    enabled: !!current,
  });
}
export function useChangePassword() {
  const { client } = useServices();
  const request = useMutation({
    mutationFn: (currentPassword: string) =>
      client.request(
        "/users/me/password/otp",
        z.object({
          expiresAt: z.string(),
          resendAvailableAt: z.string(),
          otpDeliveryStatus: z.string(),
        }),
        { method: "POST", body: { currentPassword } },
      ),
  });
  const confirm = useMutation({
    mutationFn: (body: { otp: string; newPassword: string }) =>
      client.request(
        "/users/me/password/confirm",
        z.object({ changedAt: z.string() }),
        { method: "POST", body },
      ),
  });
  return { request, confirm };
}
