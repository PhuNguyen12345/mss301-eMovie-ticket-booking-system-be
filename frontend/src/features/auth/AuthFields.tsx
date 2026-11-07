import { Field } from "../../components/ui/Field";

export function OtpField() {
  return (
    <Field label="Mã OTP">
      <input
        name="otp"
        inputMode="numeric"
        pattern="[0-9]{6}"
        minLength={6}
        maxLength={6}
        autoComplete="one-time-code"
        required
      />
    </Field>
  );
}
export function PasswordFields() {
  return (
    <>
      <Field label="Mật khẩu mới">
        <input
          name="password"
          type="password"
          minLength={8}
          maxLength={72}
          autoComplete="new-password"
          required
        />
      </Field>
      <Field label="Nhập lại mật khẩu">
        <input
          name="repeat"
          type="password"
          minLength={8}
          maxLength={72}
          autoComplete="new-password"
          required
        />
      </Field>
    </>
  );
}
export function readPassword(form: FormData) {
  const password = String(form.get("password"));
  if (password !== form.get("repeat"))
    throw new Error("Mật khẩu nhập lại không khớp.");
  if (new TextEncoder().encode(password).length > 72)
    throw new Error(
      "Mật khẩu vượt quá 72 byte. Vui lòng dùng mật khẩu ngắn hơn.",
    );
  return password;
}
