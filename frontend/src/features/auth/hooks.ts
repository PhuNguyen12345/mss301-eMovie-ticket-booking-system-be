import { useMutation } from "@tanstack/react-query";
import { z } from "zod";
import { useServices } from "../../app/ServicesProvider";

const registrationSchema = z.object({
  registrationId: z.string().uuid(),
  expiresAt: z.string().datetime({ offset: true }),
  resendAvailableAt: z.string().datetime({ offset: true }),
  otpDeliveryStatus: z.enum(["PENDING", "SENT", "FAILED"]),
});
export type Registration = z.infer<typeof registrationSchema>;
export function useRegistration() {
  const { client } = useServices();
  const request = useMutation({
    mutationFn: (body: {
      fullName: string;
      email: string;
      phone?: string;
      password: string;
    }) =>
      client.request("/auth/register", registrationSchema, {
        method: "POST",
        body,
        anonymous: true,
      }),
  });
  const resend = useMutation({
    mutationFn: (registrationId: string) =>
      client.request("/auth/register/resend-otp", registrationSchema, {
        method: "POST",
        body: { registrationId },
        anonymous: true,
      }),
  });
  const verify = useMutation({
    mutationFn: (body: { registrationId: string; otp: string }) =>
      client.request(
        "/auth/register/verify",
        z.object({
          userId: z.number().int().positive(),
          email: z.string(),
          status: z.string(),
        }),
        { method: "POST", body, anonymous: true },
      ),
  });
  return { request, resend, verify };
}
export function usePasswordReset() {
  const { client } = useServices();
  const request = useMutation({
    mutationFn: (email: string) =>
      client.request("/auth/password/forgot", z.null(), {
        method: "POST",
        body: { email },
        anonymous: true,
      }),
  });
  const confirm = useMutation({
    mutationFn: (body: { email: string; otp: string; newPassword: string }) =>
      client.request(
        "/auth/password/reset",
        z.object({ changedAt: z.string() }),
        { method: "POST", body, anonymous: true },
      ),
  });
  return { request, confirm };
}
